package com.aamotors.crm.referral;

import com.aamotors.crm.AbstractIntegrationTest;
import com.aamotors.crm.audit.AuditLogRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Year;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithMockUser(username = "admin", roles = "ADMIN")
class ReferralIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AuditLogRepository auditLogs;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("Referral lifecycle: create → confirm issues one 10 % reward → redeem once → second redemption rejected")
    void lifecycle() throws Exception {
        UUID referrer = customer("Ռեֆերեր");
        UUID invited = customer("Հրավիրված");

        MvcResult created = mvc.perform(post("/api/referrals").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("referrerCustomerId", referrer, "invitedCustomerId", invited, "notes", "Եկել է ընկերոջ խորհրդով"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.discountPercent").value(10))
                .andExpect(jsonPath("$.reward.status").value("NOT_ISSUED"))
                .andReturn();
        UUID id = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());

        // redeem before the reward exists is refused
        mvc.perform(post("/api/referrals/{id}/redeem", id).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("referral.rewardNotAvailable"));

        mvc.perform(post("/api/referrals/{id}/confirm", id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REWARDED"))
                .andExpect(jsonPath("$.reward.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.reward.issuedAt").isNotEmpty());
        assertThat(auditLogs.findByActionOrderByOccurredAtDesc("REFERRAL_REWARD_ISSUED")).isNotEmpty();

        // confirming twice cannot issue a second reward
        mvc.perform(post("/api/referrals/{id}/confirm", id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("referral.invalidTransition"));

        mvc.perform(get("/api/customers/{id}/referral-rewards", referrer))
                .andExpect(jsonPath("$.available.length()").value(1))
                .andExpect(jsonPath("$.redeemed.length()").value(0));

        UUID order = paidOrderFor(referrer);
        // an unpaid order, or another customer's order, cannot carry the discount
        UUID unpaid = orderFor(referrer, false);
        mvc.perform(post("/api/referrals/{id}/redeem", id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("serviceOrderId", unpaid))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("referral.orderNotPaid"));
        mvc.perform(post("/api/referrals/{id}/redeem", id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("serviceOrderId", paidOrderFor(invited)))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("referral.orderNotInvitedCustomer"));
        mvc.perform(post("/api/referrals/{id}/redeem", id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("serviceOrderId", order, "notes", "Յուղի փոխարինում"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reward.status").value("REDEEMED"))
                .andExpect(jsonPath("$.reward.redeemedServiceOrderId").value(order.toString()))
                .andExpect(jsonPath("$.reward.redeemedAt").isNotEmpty());
        assertThat(auditLogs.findByActionOrderByOccurredAtDesc("REFERRAL_REWARD_REDEEMED")).isNotEmpty();

        mvc.perform(post("/api/referrals/{id}/redeem", id).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("referral.rewardAlreadyRedeemed"));

        // a redeemed referral cannot be cancelled
        mvc.perform(post("/api/referrals/{id}/cancel", id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("reason", "mistake"))))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/referrals/summary"))
                .andExpect(jsonPath("$.discountPercent").value(10))
                .andExpect(jsonPath("$.redeemedDiscounts").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        mvc.perform(get("/api/referrals/analytics").param("year", String.valueOf(Year.now().getValue())))
                .andExpect(jsonPath("$.granularity").value("month"))
                .andExpect(jsonPath("$.points.length()").value(12));
    }

    @Test
    @DisplayName("Self-referrals and duplicate referrals of the same invited customer are rejected")
    void fraudPrevention() throws Exception {
        UUID a = customer("A");
        UUID b = customer("B");
        UUID c = customer("C");

        mvc.perform(post("/api/referrals").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("referrerCustomerId", a, "invitedCustomerId", a))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("referral.selfReferral"));

        mvc.perform(post("/api/referrals").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("referrerCustomerId", a, "invitedCustomerId", b))))
                .andExpect(status().isCreated());
        // the same invited customer cannot be referred again, by anyone
        mvc.perform(post("/api/referrals").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("referrerCustomerId", c, "invitedCustomerId", b))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("referral.invitedAlreadyReferred"));
        // but the referrer can earn separate rewards from different invited customers
        mvc.perform(post("/api/referrals").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("referrerCustomerId", a, "invitedCustomerId", c))))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("The 10 % discount is enforced by code and by database constraints")
    void tenPercentIsFixed() {
        assertThat(ReferralPolicy.DISCOUNT_PERCENT).isEqualTo(10);
        UUID a = jdbc.queryForObject("insert into customers (full_name, phone_e164) values ('X', '+37491000001') returning id", UUID.class);
        UUID b = jdbc.queryForObject("insert into customers (full_name, phone_e164) values ('Y', '+37491000002') returning id", UUID.class);
        assertThatThrownBy(() -> jdbc.update(
                "insert into referrals (referrer_customer_id, invited_customer_id, discount_percent) values (?, ?, 15)", a, b))
                .hasMessageContaining("ck_referrals_discount_fixed");
        assertThatThrownBy(() -> jdbc.update(
                "insert into referrals (referrer_customer_id, invited_customer_id) values (?, ?)", a, a))
                .hasMessageContaining("ck_referrals_no_self");
        jdbc.update("delete from customers where id in (?, ?)", a, b);
    }

    @Test
    @DisplayName("Cancelling a rewarded referral voids the unused discount")
    void cancelVoidsReward() throws Exception {
        UUID a = customer("P");
        UUID b = customer("Q");
        String id = json.readTree(mvc.perform(post("/api/referrals").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("referrerCustomerId", a, "invitedCustomerId", b))))
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        mvc.perform(post("/api/referrals/{id}/confirm", id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/referrals/{id}/cancel", id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("reason", "Invited customer refunded"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.reward.status").value("VOIDED"));
        mvc.perform(post("/api/referrals/{id}/redeem", id).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("referral.rewardNotAvailable"));
    }

    private UUID paidOrderFor(UUID customerId) throws Exception {
        return orderFor(customerId, true);
    }

    private UUID orderFor(UUID customerId, boolean paid) throws Exception {
        // every customer needs a vehicle to open an order
        MvcResult v = mvc.perform(post("/api/vehicles").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("customerId", customerId, "brand", "Kia", "model", "Rio",
                                "licensePlate", "99 RF " + SEQ.incrementAndGet() % 1000, "currentMileageKm", 1000))))
                .andExpect(status().isCreated()).andReturn();
        String vehicleId = json.readTree(v.getResponse().getContentAsString()).get("id").asText();
        MvcResult o = mvc.perform(post("/api/service-orders").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("vehicleId", vehicleId, "totalAmount", 25000))))
                .andExpect(status().isCreated()).andReturn();
        UUID orderId = UUID.fromString(json.readTree(o.getResponse().getContentAsString()).get("id").asText());
        if (paid) {
            mvc.perform(post("/api/service-orders/{id}/mark-paid", orderId).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.paidAt").isNotEmpty());
        }
        return orderId;
    }

    private static final java.util.concurrent.atomic.AtomicInteger SEQ = new java.util.concurrent.atomic.AtomicInteger((int) (System.nanoTime() % 90000));

    private UUID customer(String name) throws Exception {
        String phone = String.format("+37477%06d", SEQ.incrementAndGet());
        MvcResult r = mvc.perform(post("/api/customers").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("fullName", name, "phone", phone))))
                .andExpect(status().isCreated()).andReturn();
        JsonNode n = json.readTree(r.getResponse().getContentAsString());
        return UUID.fromString(n.get("id").asText());
    }
}
