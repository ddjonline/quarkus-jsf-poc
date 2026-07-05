CREATE TABLE shipment (
    pro_number         VARCHAR(32)  PRIMARY KEY,
    display_pro        VARCHAR(40)  NOT NULL,
    status             VARCHAR(24)  NOT NULL,
    origin             VARCHAR(80)  NOT NULL,
    destination        VARCHAR(80)  NOT NULL,
    shipper            VARCHAR(120) NOT NULL,
    consignee          VARCHAR(120) NOT NULL,
    commodity          VARCHAR(120) NOT NULL,
    weight_lbs         INTEGER      NOT NULL,
    pieces             INTEGER      NOT NULL,
    pickup_time        VARCHAR(48)  NOT NULL,
    driver_name        VARCHAR(80)  NOT NULL,
    driver_phone       VARCHAR(32)  NOT NULL,
    current_location   VARCHAR(120) NOT NULL,
    last_update        VARCHAR(48)  NOT NULL,
    estimated_delivery VARCHAR(48)  NOT NULL
);

CREATE TABLE tracking_event (
    id         BIGSERIAL PRIMARY KEY,
    pro_number VARCHAR(32) NOT NULL REFERENCES shipment(pro_number),
    seq        INTEGER     NOT NULL,
    time_label VARCHAR(16) NOT NULL,
    title      VARCHAR(80) NOT NULL,
    location   VARCHAR(120) NOT NULL,
    state      VARCHAR(16) NOT NULL
);
CREATE INDEX idx_event_pro_seq ON tracking_event(pro_number, seq);
