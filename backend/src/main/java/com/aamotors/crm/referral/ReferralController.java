package com.aamotors.crm.referral;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Year;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@Tag(name = "Referral bonuses", description = "Fixed 10 % one-time referral discount")
public class ReferralController {

    private final ReferralService service;

    public ReferralController(ReferralService service) {
        this.service = service;
    }

    @GetMapping("/referrals")
    public Page<ReferralDtos.ReferralView> search(@RequestParam(required = false) String q,
                                                  @RequestParam(required = false) ReferralStatus status,
                                                  @RequestParam(required = false) RewardStatus rewardStatus,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return service.search(q, status, rewardStatus, PageRequest.of(page, Math.min(Math.max(size, 1), 100)));
    }

    @GetMapping("/referrals/summary")
    public ReferralDtos.Summary summary() {
        return service.summary();
    }

    @GetMapping("/referrals/analytics")
    public ReferralDtos.Analytics analytics(@RequestParam(required = false) Integer year,
                                            @RequestParam(required = false) Integer month) {
        int y = year == null ? Year.now().getValue() : year;
        Integer m = month == null || month < 1 || month > 12 ? null : month;
        return service.analytics(y, m);
    }

    @GetMapping("/referrals/{id}")
    public ReferralDtos.ReferralView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/referrals")
    @ResponseStatus(HttpStatus.CREATED)
    public ReferralDtos.ReferralView create(@Valid @RequestBody ReferralDtos.CreateReferralRequest req) {
        return service.create(req);
    }

    @PostMapping("/referrals/{id}/confirm")
    public ReferralDtos.ReferralView confirm(@PathVariable UUID id, @RequestBody(required = false) ReferralDtos.ConfirmRequest req) {
        return service.confirm(id, req == null ? new ReferralDtos.ConfirmRequest(null, null) : req);
    }

    @PostMapping("/referrals/{id}/redeem")
    public ReferralDtos.ReferralView redeem(@PathVariable UUID id, @RequestBody(required = false) ReferralDtos.RedeemRequest req) {
        return service.redeem(id, req == null ? new ReferralDtos.RedeemRequest(null, null) : req);
    }

    @PostMapping("/referrals/{id}/cancel")
    public ReferralDtos.ReferralView cancel(@PathVariable UUID id, @Valid @RequestBody ReferralDtos.CancelRequest req) {
        return service.cancel(id, req);
    }

    @GetMapping("/customers/{id}/referral-rewards")
    public ReferralDtos.CustomerRewards customerRewards(@PathVariable UUID id) {
        return service.rewardsOf(id);
    }
}
