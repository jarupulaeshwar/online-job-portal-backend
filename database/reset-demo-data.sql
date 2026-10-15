-- Removes ALL demonstration data so the backend can load it again on its next start.
-- Real users, real companies and their jobs are not touched.
-- Run with:  mysql -u root -p job_portal < database/reset-demo-data.sql
-- Then restart the backend (SEED_DEMO_DATA must be true) to re-create the demo jobs.
USE job_portal;

DELETE n FROM application_notes n
  JOIN applications a ON a.id = n.application_id
  JOIN jobs j ON j.id = a.job_id WHERE j.is_demo = 1;
DELETE a FROM applications a JOIN jobs j ON j.id = a.job_id WHERE j.is_demo = 1;
DELETE a FROM applications a JOIN users u ON u.id = a.seeker_id WHERE u.email = 'seeker@demo.jobportal.local';
DELETE FROM saved_jobs WHERE job_id IN (SELECT id FROM (SELECT id FROM jobs WHERE is_demo = 1) x);
DELETE FROM job_reports WHERE job_id IN (SELECT id FROM (SELECT id FROM jobs WHERE is_demo = 1) x);
DELETE FROM jobs WHERE is_demo = 1;
UPDATE recruiter_profiles SET company_id = NULL
  WHERE company_id IN (SELECT id FROM (SELECT id FROM companies WHERE is_demo = 1) x);
DELETE FROM companies WHERE is_demo = 1;
