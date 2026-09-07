-- cache_key is topic|context|level|goal. Topic and context each accept 120 characters,
-- so the key reaches 266 -- past the original VARCHAR(255), which surfaced as a 500 on
-- input the API had already accepted as valid.
--
-- Widening rather than shortening the key is deliberate: hashing or truncating would
-- change every existing key, orphaning roadmaps already generated and paying to produce
-- them a second time. The unique constraint is unaffected by the type change.
alter table roadmap alter column cache_key type varchar(600);
