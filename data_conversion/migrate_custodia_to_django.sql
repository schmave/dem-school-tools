-- This script takes the database that was created by the old Custodia
-- Clojure code and its migrations and converts it to match the initial
-- state of the Django Custodia models (what is generated by
-- django/custodia/migrations/0001_initial.py)
--
-- If you never did local development of Custodia with the Clojure code,
-- you won't need to run this.


\set ON_ERROR_STOP 1

BEGIN;

-- Copy over columns from overseer.students to person
UPDATE person p set custodia_show_as_absent=s.show_as_absent
   FROM overseer.students s where s.dst_id=p.person_id;
UPDATE person p set custodia_start_date=s.start_date
   FROM overseer.students s where s.dst_id=p.person_id;



alter table overseer.excuses add column person_id  int4 null;
ALTER TABLE overseer.excuses ADD CONSTRAINT fk_excuses_person FOREIGN KEY (person_id) REFERENCES public.person(person_id);
alter table overseer.swipes add column person_id  int4 null;
ALTER TABLE overseer.swipes ADD CONSTRAINT fk_person FOREIGN KEY (person_id) REFERENCES public.person(person_id);
alter table overseer.students_required_minutes add column person_id  int4 null;
ALTER TABLE overseer.students_required_minutes ADD CONSTRAINT fk_person FOREIGN KEY (person_id) REFERENCES public.person(person_id);
alter table overseer.overrides add column person_id  int4 null;
ALTER TABLE overseer.overrides ADD CONSTRAINT fk_person FOREIGN KEY (person_id) REFERENCES public.person(person_id);

UPDATE overseer.excuses e set person_id=s.dst_id
   FROM overseer.students s, public.person p where s._id=e.student_id and s.dst_id=p.person_id;
delete FROM overseer.excuses where person_id is null;
UPDATE overseer.swipes e set person_id=s.dst_id
   FROM overseer.students s, public.person p where s._id=e.student_id and s.dst_id=p.person_id;
delete FROM overseer.swipes where person_id is null;
UPDATE overseer.students_required_minutes e set person_id=s.dst_id
   FROM overseer.students s, public.person p where s._id=e.student_id and s.dst_id=p.person_id;
delete FROM overseer.students_required_minutes where person_id is null;
UPDATE overseer.overrides e set person_id=s.dst_id
   FROM overseer.students s, public.person p where s._id=e.student_id and s.dst_id=p.person_id;
delete FROM overseer.overrides where person_id is null;

alter table overseer.excuses alter column person_id set not null;
alter table overseer.swipes alter column person_id set not null;
alter table overseer.students_required_minutes alter column person_id set not null;
alter table overseer.overrides alter column person_id set not null;


DELETE FROM overseer.excuses a USING (
    SELECT MIN(ctid) as ctid, person_id, "date"
    FROM overseer.excuses
    GROUP BY person_id, "date" HAVING COUNT(*) > 1
) b
WHERE a.person_id = b.person_id and a.date=b.date
AND a.ctid <> b.ctid;
alter table overseer.excuses add constraint uniq_excuses_person_date unique(person_id, "date");

DELETE FROM overseer.overrides a USING (
    SELECT MIN(ctid) as ctid, person_id, "date"
    FROM overseer.overrides
    GROUP BY person_id, "date" HAVING COUNT(*) > 1
) b
WHERE a.person_id = b.person_id and a.date=b.date
AND a.ctid <> b.ctid;
alter table overseer.overrides add constraint uniq_overrides_person_date unique(person_id, "date");

alter table overseer.swipes drop column rounded_in_time;
alter table overseer.swipes drop column rounded_out_time;
alter table overseer.swipes drop column intervalmin;
create index on overseer.swipes (person_id, swipe_day);
create index on overseer.swipes (swipe_day, person_id);
alter table overseer.swipes alter column swipe_day set not null;

alter table overseer.excuses drop column student_id;
alter table overseer.swipes drop column student_id cascade;
alter table overseer.students_required_minutes drop column student_id cascade;
alter table overseer.overrides drop column student_id;


-- Combine overseer.schools with public.organization, and fix a bunch of things
-- about the years table.

UPDATE organization o set timezone=s.timezone FROM overseer.schools s where s._id=o.id;

ALTER TABLE overseer.years RENAME COLUMN school_id to organization_id;
ALTER TABLE overseer.years DROP CONSTRAINT fk_class_school;
ALTER TABLE overseer.years ADD CONSTRAINT fk_org FOREIGN KEY (organization_id) REFERENCES organization(id);
ALTER TABLE overseer.years ALTER COLUMN organization_id DROP DEFAULT;
ALTER TABLE overseer.years ALTER COLUMN name SET NOT NULL;

ALTER TABLE overseer.years ALTER COLUMN inserted_date SET NOT NULL;
ALTER TABLE overseer.years ALTER COLUMN inserted_date DROP DEFAULT;
ALTER TABLE overseer.years ALTER COLUMN inserted_date type timestamp with time zone;

ALTER TABLE overseer.excuses ALTER COLUMN inserted_date SET NOT NULL;
ALTER TABLE overseer.excuses ALTER COLUMN "date" SET NOT NULL;
ALTER TABLE overseer.excuses ALTER COLUMN inserted_date DROP DEFAULT;
ALTER TABLE overseer.excuses ALTER COLUMN inserted_date type timestamp with time zone;

ALTER TABLE overseer.overrides ALTER COLUMN inserted_date SET NOT NULL;
ALTER TABLE overseer.overrides ALTER COLUMN "date" SET NOT NULL;
ALTER TABLE overseer.overrides ALTER COLUMN inserted_date DROP DEFAULT;
ALTER TABLE overseer.overrides ALTER COLUMN inserted_date type timestamp with time zone;

delete from overseer.years where from_date is null or to_date is null;
ALTER TABLE overseer.years ALTER COLUMN from_date SET NOT NULL;
ALTER TABLE overseer.years ALTER COLUMN to_date SET NOT NULL;

ALTER TABLE overseer.years RENAME COLUMN from_date to from_time;
ALTER TABLE overseer.years RENAME COLUMN to_date to to_time;


-- Delete duplicate organization_id, name
DELETE FROM overseer.years a USING (
    SELECT MIN(ctid) as ctid, organization_id, name
    FROM overseer.years
    GROUP BY organization_id, name HAVING COUNT(*) > 1
) b
WHERE a.organization_id = b.organization_id and a.name=b.name
AND a.ctid <> b.ctid;
ALTER TABLE overseer.years ADD CONSTRAINT unique_name UNIQUE (organization_id, name);

-- Add unique ID to students_required_minutes table
ALTER TABLE overseer.students_required_minutes ADD COLUMN id SERIAL PRIMARY KEY;

-- Disallow null in times for swipes
DELETE FROM overseer.swipes WHERE in_time is NULL;
ALTER TABLE overseer.swipes ALTER COLUMN in_time SET NOT NULL;


-- Move tables that we are saving out of overseer schema and rename to Django-style names
ALTER TABLE overseer.students_required_minutes set schema public;
ALTER TABLE overseer.swipes set schema public;
ALTER TABLE overseer.overrides set schema public;
ALTER TABLE overseer.excuses set schema public;
ALTER TABLE overseer.years set schema public;

ALTER TABLE swipes RENAME COLUMN _id  to id;
ALTER TABLE overrides RENAME COLUMN _id  to id;
ALTER TABLE excuses RENAME COLUMN _id  to id;
ALTER TABLE years RENAME COLUMN _id  to id;

ALTER TABLE students_required_minutes RENAME TO custodia_studentrequiredminutes;
ALTER TABLE swipes RENAME TO custodia_swipe;
ALTER TABLE overrides RENAME TO custodia_override;
ALTER TABLE excuses RENAME TO custodia_excuse;
ALTER TABLE years RENAME TO custodia_year;

COMMIT;