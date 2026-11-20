package com.example.insurance.policyservice.policy.service;

import com.example.insurance.policyservice.catalogue.api.CatalogueApi;
import com.example.insurance.policyservice.catalogue.api.CatalogueApiException;
import com.example.insurance.policyservice.catalogue.api.EligibilityProblem;
import com.example.insurance.policyservice.catalogue.api.MemberInput;
import com.example.insurance.policyservice.catalogue.api.PlanForSale;
import com.example.insurance.policyservice.catalogue.api.QuotedPrice;
import com.example.insurance.policyservice.config.BusinessCalendar;
import com.example.insurance.policyservice.exception.ApiErrorsException;
import com.example.insurance.policyservice.exception.ApiException;
import com.example.insurance.policyservice.exception.ErrorCode;
import com.example.insurance.policyservice.policy.domain.Quote;
import com.example.insurance.policyservice.policy.domain.QuoteStatus;
import com.example.insurance.policyservice.policy.domain.QuotedMember;
import com.example.insurance.policyservice.policy.dto.PremiumView;
import com.example.insurance.policyservice.policy.dto.QuoteMemberRequest;
import com.example.insurance.policyservice.policy.dto.QuoteRequest;
import com.example.insurance.policyservice.policy.dto.QuoteResponse;
import com.example.insurance.policyservice.policy.repository.QuoteRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;

/**
 * Quotes (PLAN 8.2): validate the start date, the plan, eligibility and the module selection, then
 * price on the server and keep the offer for 30 days.
 */
@Service
public class QuoteService {

  static final Duration VALIDITY = Duration.ofDays(30);
  static final int MAX_DAYS_AHEAD = 60;
  private static final Set<String> SELECTION_CODES =
      Set.of("MANDATORY_MODULE_MISSING", "MODULE_NOT_FOUND", "OPTION_NOT_FOUND");
  private static final TypeReference<List<QuotedMember>> MEMBERS = new TypeReference<>() {};
  private static final TypeReference<Map<String, String>> SELECTION = new TypeReference<>() {};

  private final CatalogueApi catalogue;
  private final QuoteRepository quotes;
  private final Clock clock;
  private final BusinessCalendar calendar;

  public QuoteService(
      CatalogueApi catalogue, QuoteRepository quotes, Clock clock, BusinessCalendar calendar) {
    this.catalogue = catalogue;
    this.quotes = quotes;
    this.clock = clock;
    this.calendar = calendar;
  }

  @Transactional
  public QuoteResponse create(UUID userId, QuoteRequest request) {
    LocalDate today = calendar.today();
    requireStartInWindow(request.startDate(), today);
    PlanForSale plan =
        catalogue
            .planOnSale(request.planId(), today)
            .orElseThrow(() -> new ApiException(ErrorCode.PLAN_NOT_FOUND, "No such plan on sale."));
    List<MemberInput> members =
        request.members().stream()
            .map(m -> new MemberInput(m.relationship(), m.sex(), m.dateOfBirth(), m.student()))
            .toList();
    List<EligibilityProblem> problems =
        catalogue.checkEligibility(
            plan.planId(), request.coverType(), members, request.startDate());
    if (!problems.isEmpty()) {
      throw new ApiErrorsException(
          ErrorCode.NOT_ELIGIBLE,
          "The members cannot be covered by this plan. See errors.",
          problems.stream().map(QuoteService::problem).toList());
    }
    QuotedPrice price =
        price(plan.planId(), request.selectedModules(), members, request.startDate());

    Instant now = clock.instant();
    List<QuotedMember> quotedMembers =
        request.members().stream()
            .map(m -> new QuotedMember(m.relationship(), m.sex(), m.dateOfBirth(), m.student()))
            .toList();
    Quote quote =
        Quote.open(
            new Quote.Terms(
                userId,
                plan.insurerId(),
                plan.insurerCode(),
                plan.planId(),
                plan.planCode(),
                plan.planVersion(),
                request.coverType(),
                request.startDate(),
                PolicyJson.write(quotedMembers),
                PolicyJson.write(new LinkedHashMap<>(request.selectedModules())),
                price.annualPremium(),
                PolicyJson.write(Premiums.of(price)),
                price.currency()),
            now,
            now.plus(VALIDITY));
    return toResponse(quotes.save(quote), now);
  }

  @Transactional(readOnly = true)
  public QuoteResponse get(UUID userId, UUID quoteId) {
    return toResponse(own(userId, quoteId), clock.instant());
  }

  /** The caller's own quote; another customer's quote is not found (CLAUDE.md). */
  Quote own(UUID userId, UUID quoteId) {
    return quotes
        .findByIdAndHolderUserId(quoteId, userId)
        .orElseThrow(() -> new ApiException(ErrorCode.QUOTE_NOT_FOUND, "No such quote."));
  }

  /** Cover starts from tomorrow up to 60 days ahead (D35). */
  static void requireStartInWindow(LocalDate startDate, LocalDate today) {
    if (!startDate.isAfter(today) || startDate.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
      throw new ApiException(
          ErrorCode.START_DATE_OUT_OF_RANGE,
          "Cover must start between tomorrow and " + MAX_DAYS_AHEAD + " days from today.");
    }
  }

  private QuotedPrice price(
      UUID planId, Map<String, String> selection, List<MemberInput> members, LocalDate start) {
    try {
      return catalogue.price(planId, selection, members, start);
    } catch (CatalogueApiException e) {
      if (CatalogueApiException.PLAN_NOT_FOUND.equals(e.code())) {
        throw new ApiException(ErrorCode.PLAN_NOT_FOUND, "No such plan on sale.");
      }
      ErrorCode code =
          SELECTION_CODES.contains(e.code())
              ? ErrorCode.MODULE_SELECTION_INVALID
              : ErrorCode.NOT_ELIGIBLE;
      throw new ApiErrorsException(
          code,
          "The quote cannot be priced. See errors.",
          List.of(Map.of("code", e.code(), "message", e.getMessage())));
    }
  }

  private static Map<String, Object> problem(EligibilityProblem p) {
    Map<String, Object> error = new LinkedHashMap<>();
    error.put("code", p.code());
    if (p.memberIndex() != null) {
      error.put("memberIndex", p.memberIndex());
    }
    error.put("message", p.message());
    return error;
  }

  static List<QuotedMember> members(Quote quote) {
    return PolicyJson.read(quote.getMembers(), MEMBERS);
  }

  static Map<String, String> selection(Quote quote) {
    return PolicyJson.read(quote.getSelectedModules(), SELECTION);
  }

  private static QuoteResponse toResponse(Quote quote, Instant now) {
    String status =
        quote.getStatus() == QuoteStatus.OPEN && quote.expiredAt(now)
            ? "EXPIRED"
            : quote.getStatus().name();
    return new QuoteResponse(
        quote.getId(),
        quote.getPlanId(),
        quote.getInsurerCode(),
        quote.getPlanCode(),
        quote.getPlanVersion(),
        quote.getCoverType(),
        quote.getStartDate(),
        members(quote).stream()
            .map(m ->
                new QuoteMemberRequest(m.relationship(), m.sex(), m.dateOfBirth(), m.student()))
            .toList(),
        selection(quote),
        PolicyJson.read(quote.getBreakdown(), PremiumView.class),
        quote.getValidUntil(),
        status);
  }
}
