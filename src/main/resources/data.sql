-- noinspection SqlResolveForFile

/* insert com.foriatickets.foriabackend.entities.VenueEntity */
insert
        into
            `venue` (
                `contact_city`, `contact_country`, `contact_email`, `contact_name`, `contact_phone`, `contact_phone_country`, `contact_state`, `contact_street_address`, `contact_postal`, `description`, `name`, `id`)
        values
            ('Fake City', 'USA', 'john.doe@test.com', 'John Doe', '5555555555', '+1', 'MO', '12345 Maple Ln', '55555', 'Testing Venue', 'Test Venue', '2b2c8c0f-5b17-429a-a5f6-44dd47935e9a');

/* insert com.foriatickets.foriabackend.entities.EventEntity */
insert
        into
            `event` (
                `authorized_tickets`, `event_start_time`, `event_end_time`, `name`, `venue_id`, `id`, `image_url`, `tag_line`, `description`, `status`, `visibility`
            )
        values
            (100, '2019-12-22T16:23:25.847Z', '2020-12-22T18:23:25.847Z', 'Test Event', '2b2c8c0f-5b17-429a-a5f6-44dd47935e9a', '40c6a84f-9505-40b9-a9ea-817074aac2f1', 'https://localhost', 'For the Fans', 'This is a test event.', 'LIVE', 'PUBLIC');

/* insert com.foriatickets.foriabackend.entities.TicketFeeConfigEntity */
insert
        into
            `ticket_fee_config` (
                `price`, `currency`, `description`, `status`, `event_id`, `method`, `name`, `type`, `id`
            )
        values
            (10.00, 'USD', 'Test Flat', 'ACTIVE', '40c6a84f-9505-40b9-a9ea-817074aac2f1', 'FLAT', 'Flat 10 Test', 'ISSUER', 'a7760a43-54ec-407a-8bee-7709dfb67bf6');

/* insert com.foriatickets.foriabackend.entities.TicketFeeConfigEntity*/
insert
        INTO
            `ticket_fee_config` (
                `price`, `currency`, `description`, `status`, `event_id`, `method`, `name`, `type`, `id`
            )
        values
            (0.01, 'USD', 'Test Percent', 'ACTIVE', '40c6a84f-9505-40b9-a9ea-817074aac2f1', 'PERCENT', 'Percent 0.01 Test', 'ISSUER', 'f966ba1d-32b5-4507-ab9d-d9b867c6c843');

/* insert com.foriatickets.foriabackend.entities.TicketTypeConfigEntity */
insert
INTO
    `ticket_type_config` (
    `authorized_amount`, `currency`, `description`, `status`, `type`, `event_id`, `name`, `price`, `id`
)
values
(100, 'USD', 'Free', 'ACTIVE', 'PUBLIC', '40c6a84f-9505-40b9-a9ea-817074aac2f1', 'Free', 0.00, '9c0f3a04-a4f6-4229-9e8b-2ee9c3ec5fee');

/* insert com.foriatickets.foriabackend.entities.TicketTypeConfigEntity */
insert
        INTO
            `ticket_type_config` (
                `authorized_amount`, `currency`, `description`, `status`, `type`,  `event_id`, `name`, `price`, `id`
            )
        values
            (100, 'USD', 'Test', 'ACTIVE', 'PUBLIC', '40c6a84f-9505-40b9-a9ea-817074aac2f1', 'GA Test', 100.00, '9c0f3a04-a4f6-4229-9e8b-2ee9c3ec5f18');

/* insert com.foriatickets.foriabackend.entities.UserEntity */
INSERT
    INTO
        `user` (
                "id", "auth0_id", "email", "first_name", "last_name", "stripe_id"
        )
        VALUES
            ('3f4bc13d-3bc3-4c41-84ed-c92193bac935', 'test', 'test@foriatickets.com', 'Corbin', 'Schwalm', null);

/* insert com.foriatickets.foriabackend.entities.UserEntity */
INSERT
INTO
    `user` (
    "id", "auth0_id", "email", "first_name", "last_name", "stripe_id"
)
VALUES
('9f4bc13d-3bc3-4c41-84ed-c92193bac935', 'auth0|5d6e29e4f820bf0eca4740e1', 'corbin@foriatickets.com', 'Corbin', 'Schwalm', null);

/* insert com.foriatickets.foriabackend.entities.UserEntity */
INSERT
INTO
    `venue_access` (
    "id", "user_id", "venue_id", "created_d"
)
VALUES
('9f4bc13d-3bc3-4c41-84ed-c92193bac111', '3f4bc13d-3bc3-4c41-84ed-c92193bac935', '2b2c8c0f-5b17-429a-a5f6-44dd47935e9a', '2019-01-01 00:00:00');

/* insert com.foriatickets.foriabackend.entities.UserEntity */
INSERT
INTO
    `venue_access` (
    "id", "user_id", "venue_id", "created_d"
)
VALUES
('9f4bc13d-3bc3-4c41-84ed-c92193bac1115', '9f4bc13d-3bc3-4c41-84ed-c92193bac935', '2b2c8c0f-5b17-429a-a5f6-44dd47935e9a', '2019-01-01 00:00:00');
