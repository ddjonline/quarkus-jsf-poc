package app.freighttrack.web;

import app.freighttrack.domain.*;
import app.freighttrack.service.ShipmentService;
import app.freighttrack.session.RedisSessionStore;
import app.freighttrack.session.SessionContext;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Named("tracking")
@RequestScoped
public class TrackingBean {

    @Inject
    SessionContext session;

    @Inject
    RedisSessionStore store;

    @Inject
    ShipmentService shipmentService;

    private TrackingSearch search;

    @PostConstruct
    public void init() {
        search = store.load(session.getId());
    }

    public List<ProNumberEntry> getEntries() {
        return search.getEntries();
    }

    public List<ShipmentLookupResult> getResults() {
        return search.getResults();
    }

    public int getCount() {
        return search.getCount();
    }

    public int getMax() {
        return TrackingConstants.MAX_PRO_NUMBERS;
    }

    public String getCounterText() {
        return search.getCount() + "/" + TrackingConstants.MAX_PRO_NUMBERS;
    }

    public boolean isAddDisabled() {
        return search.getCount() >= TrackingConstants.MAX_PRO_NUMBERS;
    }

    public boolean isRemoveVisible() {
        return search.getCount() > TrackingConstants.MIN_PRO_NUMBERS;
    }

    public boolean isSearchActive() {
        return search.getEntries().stream().anyMatch(e -> e.getChecked() && !e.isBlank());
    }

    public String getFindButtonLabel() {
        int n = (int) search.getEntries().stream()
                .filter(e -> e.getChecked() && !e.isBlank()).count();
        return n <= 1 ? "Find My Shipment" : "Find My Shipments (" + n + ")";
    }

    public boolean isHasResults() {
        return search.getHasResults();
    }

    public int getResultsCount() {
        return search.getResults().size();
    }

    public void addNumber() {
        if (search.getCount() < TrackingConstants.MAX_PRO_NUMBERS) {
            search.getEntries().add(new ProNumberEntry());
            store.save(session.getId(), search);
        }
    }

    public void removeNumber(String rowId) {
        if (search.getCount() > TrackingConstants.MIN_PRO_NUMBERS) {
            ProNumberEntry removed = search.getEntries().stream()
                    .filter(e -> e.getId().equals(rowId))
                    .findFirst().orElse(null);
            search.getEntries().removeIf(e -> e.getId().equals(rowId));
            if (removed != null && !removed.isBlank()) {
                String normalized = removed.normalized();
                search.getResults().removeIf(r -> r.getQueriedPro().equals(normalized));
            }
            store.save(session.getId(), search);
        }
    }

    public void validateEntry(String rowId) {
        search.getEntries().stream()
                .filter(e -> e.getId().equals(rowId))
                .findFirst()
                .ifPresent(e -> e.setChecked(e.isValid()));
        store.save(session.getId(), search);
    }

    public void findShipments() {
        List<String> pros = search.getEntries().stream()
                .filter(e -> e.getChecked() && !e.isBlank())
                .map(ProNumberEntry::normalized)
                .collect(Collectors.toList());

        List<ShipmentLookupResult> results = shipmentService.lookup(pros);

        List<ShipmentLookupResult> withExpanded = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            ShipmentLookupResult r = results.get(i);
            boolean shouldExpand = i == 0 && r.getFound();
            withExpanded.add(new ShipmentLookupResult(
                    r.getQueriedPro(), r.getDisplayPro(), r.getState(),
                    r.getShipment(), shouldExpand, r.getErrorMessage()));
        }

        search.getResults().clear();
        search.getResults().addAll(withExpanded);
        store.save(session.getId(), search);
    }

    public void lookupShipment(String pro) {
        search.getResults().stream()
                .filter(r -> r.getQueriedPro().equals(pro))
                .findFirst()
                .ifPresent(r -> r.setState(LookupState.LOADING));
        store.save(session.getId(), search);
    }

    public void toggleExpand(String pro) {
        search.getResults().stream()
                .filter(r -> r.getQueriedPro().equals(pro))
                .findFirst()
                .ifPresent(r -> r.setExpanded(!r.getExpanded()));
        store.save(session.getId(), search);
    }

    public void expandFirst() {
        search.getResults().stream()
                .filter(r -> r.getFound())
                .findFirst()
                .ifPresent(r -> r.setExpanded(true));
        store.save(session.getId(), search);
    }

    public void reset() {
        search.getEntries().clear();
        search.getEntries().add(new ProNumberEntry());
        search.getResults().clear();
        store.save(session.getId(), search);
    }
}
