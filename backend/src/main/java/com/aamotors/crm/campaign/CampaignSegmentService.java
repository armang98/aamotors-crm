package com.aamotors.crm.campaign;

import com.aamotors.crm.customer.Customer;
import com.aamotors.crm.messaging.channel.Channel;
import com.aamotors.crm.messaging.channel.ChannelProvider;
import com.aamotors.crm.messaging.channel.ChannelRegistry;
import com.aamotors.crm.vehicle.Vehicle;
import com.aamotors.crm.vehicle.VehicleServiceRecord;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Resolves a {@link CampaignFilters} segment to customers (one row per customer, with the
 * matching vehicle). Vehicle/filter criteria are matched against the vehicle's latest
 * service record; consent criteria against customer consents. Eligibility uses the
 * same channel providers as Quick Advertising.
 */
@Service
public class CampaignSegmentService {

    public record Member(Customer customer, Vehicle vehicle, Channel firstChannel, String reason) {
        public boolean eligible() { return firstChannel != null; }
    }

    private final EntityManager em;
    private final ChannelRegistry channels;

    public CampaignSegmentService(EntityManager em, ChannelRegistry channels) {
        this.em = em;
        this.channels = channels;
    }

    @Transactional(readOnly = true)
    public List<Member> resolve(CampaignFilters f) {
        if (f == null) f = CampaignFilters.empty();
        boolean vehicleCriteria = f.brand() != null || f.model() != null || f.lastServiceFrom() != null || f.lastServiceTo() != null
                || f.oilType() != null || f.oilName() != null || f.oilFilterName() != null || f.oilFilterCode() != null
                || f.cabinFilterName() != null || f.cabinFilterCode() != null || f.airFilterName() != null || f.airFilterCode() != null
                || f.nextOilChangeMileageMax() != null;

        Map<UUID, Member> byCustomer = new LinkedHashMap<>();
        if (vehicleCriteria) {
            // the latest visit per vehicle = the one with the max service date (ties are harmless: one row per customer is kept)
            StringBuilder jpql = new StringBuilder("select v, r from Vehicle v join fetch v.customer c left join VehicleServiceRecord r on r.vehicle = v " +
                    "and r.serviceDate = (select max(r3.serviceDate) from VehicleServiceRecord r3 where r3.vehicle = v) where 1=1");
            Map<String, Object> params = new HashMap<>();
            like(jpql, params, "v.brand", "brand", f.brand());
            like(jpql, params, "v.model", "model", f.model());
            like(jpql, params, "r.oilName", "oilName", f.oilName());
            like(jpql, params, "r.oilFilterName", "oilFilterName", f.oilFilterName());
            like(jpql, params, "r.oilFilterCode", "oilFilterCode", f.oilFilterCode());
            like(jpql, params, "r.cabinFilterName", "cabinFilterName", f.cabinFilterName());
            like(jpql, params, "r.cabinFilterCode", "cabinFilterCode", f.cabinFilterCode());
            like(jpql, params, "r.airFilterName", "airFilterName", f.airFilterName());
            like(jpql, params, "r.airFilterCode", "airFilterCode", f.airFilterCode());
            if (f.oilType() != null) { jpql.append(" and r.oilType = :oilType"); params.put("oilType", f.oilType()); }
            if (f.lastServiceFrom() != null) { jpql.append(" and r.serviceDate >= :from"); params.put("from", f.lastServiceFrom()); }
            if (f.lastServiceTo() != null) { jpql.append(" and r.serviceDate <= :to"); params.put("to", f.lastServiceTo()); }
            if (f.nextOilChangeMileageMax() != null) {
                jpql.append(" and v.nextOilChangeMileageKm is not null and (v.nextOilChangeMileageKm - v.currentMileageKm) <= :kmMax");
                params.put("kmMax", f.nextOilChangeMileageMax());
            }
            jpql.append(" order by c.fullName");
            TypedQuery<Object[]> q = em.createQuery(jpql.toString(), Object[].class);
            params.forEach(q::setParameter);
            for (Object[] row : q.getResultList()) {
                Vehicle v = (Vehicle) row[0];
                byCustomer.putIfAbsent(v.getCustomer().getId(), evaluate(v.getCustomer(), v, f));
            }
        } else {
            List<Customer> all = em.createQuery("select c from Customer c order by c.fullName", Customer.class).getResultList();
            for (Customer c : all) byCustomer.putIfAbsent(c.getId(), evaluate(c, null, f));
        }
        return new ArrayList<>(byCustomer.values());
    }

    private Member evaluate(Customer c, Vehicle v, CampaignFilters f) {
        if (f.consentChannels() != null && !f.consentChannels().isEmpty()) {
            boolean any = f.consentChannels().stream().anyMatch(ch -> channels.get(ch).eligibility(c).eligible());
            if (!any) return new Member(c, v, null, "CONSENT_FILTER");
        }
        for (ChannelProvider p : channels.inFallbackOrder()) {
            if (p.eligibility(c).eligible()) return new Member(c, v, p.channel(), null);
        }
        return new Member(c, v, null, "NO_ELIGIBLE_CHANNEL");
    }

    private static void like(StringBuilder jpql, Map<String, Object> params, String field, String name, String value) {
        if (value == null || value.isBlank()) return;
        jpql.append(" and lower(").append(field).append(") like :").append(name);
        params.put(name, "%" + value.trim().toLowerCase() + "%");
    }

    static String vehicleLabel(Vehicle v) {
        return v == null ? null : v.getBrand() + " " + v.getModel() + " · " + v.getLicensePlate();
    }
}
