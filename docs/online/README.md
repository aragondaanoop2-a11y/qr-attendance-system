# Attendly cloud portal setup

This is the shared online version of Attendly. Faculty and students can use it from different networks. Attendance is stored in your Supabase project. The Java desktop app and same-Wi-Fi demo remain separate.

## Before students use it

1. Create a free project at [supabase.com](https://supabase.com/). Keep the project in your own account.
2. In the Supabase project, open **SQL Editor**, choose **New query**, paste the whole contents of **setup.sql**, and click **Run**. This creates the profiles, sessions, private QR tokens, attendance records, role rules, and protected database functions.
3. Open **Project Settings → API Keys** (or **Settings → API** in older dashboards). Copy the **Project URL** and the **publishable key**. An older project may call this the **anon/public key**.
4. Edit **config.js** in the GitHub repository. Put those two values between the quotes for **supabaseUrl** and **publishableKey**. Save and commit the change. The URL and publishable/anon key are intended for browser use when row-level security is enabled. Never copy a **secret** or **service_role** key into GitHub or this website.
5. In Supabase, open **Authentication → URL Configuration**. Set **Site URL** to the online portal home, for example https://aragondaanoop2-a11y.github.io/qr-attendance-system/online/. Add the exact URL https://aragondaanoop2-a11y.github.io/qr-attendance-system/online/auth.html to **Redirect URLs**.
6. Create the faculty account in **Authentication → Users → Add user**. Enter the faculty email and a strong password. Then open **SQL Editor** and run the following command, replacing the sample email with that same email:

   UPDATE public.qr_profiles AS p
   SET role = 'faculty'
   FROM auth.users AS u
   WHERE p.id = u.id AND lower(u.email) = lower('faculty@example.com');

   The account must be created after **setup.sql** has been run, so the profile trigger can create its profile. The command should update one row.
7. Publish or wait for GitHub Pages to publish the changed files. Open the online portal home and sign in with the faculty account. Students open **Student portal → Create student account**, verify their email if Supabase asks, then sign in.
8. Faculty starts a session and displays the QR code. A student scans it on any network, signs in on that phone if needed, and the check-in is recorded in Supabase. Faculty can view and export the shared attendance list.

## What the roles can do

- New accounts are always students. Public sign-up cannot grant faculty access.
- Faculty access is assigned by the Supabase project owner in SQL Editor.
- A student checks in only while signed in to their student account and only with the unexpired QR token for an active session.
- The database rejects duplicate check-ins for the same student and session.
- Attendance and profile queries are protected by row-level security. Students cannot read other students' records.
- Closing a session or letting it expire disables its QR check-in.

## If something does not work

- **Setup required**: **config.js** still needs the Supabase Project URL and publishable key.
- **Invalid API key / failed to fetch**: recheck the Project URL and publishable key in **config.js**; make sure Supabase is online.
- **Profile not found**: run **setup.sql** before creating accounts. Delete the test account from Supabase Auth and create it again so the trigger makes its profile.
- **Faculty portal says student account**: run the role promotion SQL above with the faculty user's exact email.
- **Email verification link fails**: confirm the portal's **auth.html** address is listed under Supabase **Redirect URLs**.
- **Student ID already used**: each student ID must be unique. The same student should sign in to the existing account instead of registering again.

For the project owner only: **setup.sql** is designed for a new Supabase project. Do not rerun it on top of a modified production schema without reviewing the changes first.
