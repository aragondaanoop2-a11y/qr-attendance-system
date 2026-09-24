# Attendly web demo

This static companion site has separate Faculty and Student portals. It can be hosted from this `docs` folder with GitHub Pages.

## Pages

- `index.html` — choose a portal.
- `faculty.html` — start a 10-minute demo session, show its QR code, search sample attendance, and export a CSV.
- `student.html` — open the faculty QR link and submit a demo check-in.

## Shared cloud portal

The separate portal in online/ uses Supabase so faculty and students can sign in from different networks and save attendance in one shared database. Follow online/README.md to create the Supabase project, run its SQL setup, and connect the public website. Do not enter real records until you have completed that setup.

## Browser demo limitations

The site has no server or database. It stores check-ins in the current browser only, so attendance entered on a student's phone will not appear on the faculty member's computer. The sample data is fictitious. Do not enter real student information. A production deployment needs a shared backend and authenticated accounts.

## Publish with GitHub Pages

1. Push this project to GitHub.
2. Open the repository's **Settings → Pages**.
3. Under **Build and deployment**, choose **Deploy from a branch**.
4. Select the `main` branch and the `/docs` folder, then save.
5. Wait for the Pages deployment to finish; GitHub will show the public site URL on that page.
