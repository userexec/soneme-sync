# Soneme Sync

![Soneme Sync Icon](https://github.com/userexec/soneme-sync/blob/master/soneme-sync-icon.png?raw=true)

Soneme Sync is a small, keypad-friendly Android file synchronization utility built for moving files between a Sonim XP3900 and FTP or SFTP servers without cloud accounts, sync services, scheduled jobs, or Google Play Services.

It is intentionally simple. You define local folders on the phone, define remote folders on servers, then create Jobs that copy from one to the other when you manually run them. A Job can upload from Local to Remote or download from Remote to Local.

Soneme Sync is not rsync, a two-way reconciliation tool, or a backup suite with opinions about timestamps. It follows one deliberately simple rule:

**If the destination already has something with that name, leave it alone.**

That makes it useful for things like copying new photos to a NAS, pulling down a folder of files for offline use, or other jobs where "send me the things I don't already have" is exactly what you want.

The interface is designed around the XP3900's D-pad and three Sonim softkeys. There are no touch controls.

![Job running interface](https://github.com/userexec/soneme-sync/blob/master/screenshot-jobrun.png?raw=true)  ![Jobs interface](https://github.com/userexec/soneme-sync/blob/master/screenshot-jobs.png?raw=true)  ![Locals interface](https://github.com/userexec/soneme-sync/blob/master/screenshot-locals.png?raw=true)  ![Remotes interface](https://github.com/userexec/soneme-sync/blob/master/screenshot-remotes.png?raw=true)  ![Local interface](https://github.com/userexec/soneme-sync/blob/master/screenshot-local.png?raw=true)  ![Remote interface](https://github.com/userexec/soneme-sync/blob/master/screenshot-remote.png?raw=true)

## Features

* Manual Local-to-Remote and Remote-to-Local sync Jobs
* Multiple configurable Locals, Remotes, and Jobs
* FTP and SFTP support
* Passive-mode plain FTP
* Conventional anonymous FTP login when username and password are blank
* Password-authenticated SFTP
* Recursive folder synchronization
* Dotfile support
* Name-only transfer decisions
* No overwriting of existing destination files or folders
* Temporary `.soneme-part` files to avoid presenting incomplete transfers as finished files
* Cleanup of stale partial transfers at the beginning of the next run
* Per-Job run logs and last-run status
* Connection testing for Remotes
* Foreground sync operation with the screen off or flip closed
* One-minute inactivity timeout
* Sonim softkey integration
* No accounts, analytics, advertising, subscriptions, or Google Play Services

## Tested Devices

Soneme Sync has been developed and tested on:

* Sonim XP3plus XP3900 — Android 11 Go

The app uses normal Android APIs where practical, but the interface and softkey behavior are specifically designed for the XP3900's 240x320 non-touch display.

Other Android devices are not a target. In particular, a normal touchscreen phone probably will not have the Sonim softkeys the interface expects.

## Installing

Soneme Sync is distributed as a normal Android APK.

Copy the APK to the device and install it, or install it from a connected computer with ADB:

    adb install soneme-sync.apk

If updating an existing release signed with the same release key:

    adb install -r soneme-sync.apk

Android may require permission to install apps from unknown sources when installing directly on the phone.

## How Soneme Sync Is Organized

The main interface has three tabs:

### Jobs

Jobs are the sync operations you actually run.

Each Job has:

* a name,
* a source,
* a destination,
* a last-run time,
* and a last-run result.

One side of a Job must be a Local and the other must be a Remote.

Selecting a Job runs it. The softkey menu can also show Job information, move the Job upward in the list, or create a new Job.

### Locals

Locals are folders on the phone.

A Local can point to internal storage or removable storage. Soneme Sync uses Android's system folder picker and remembers access to the selected folder.

Each Local has a friendly name so Jobs do not need to care about Android's underlying storage URI.

Examples might be:

* `Camera`
* `Downloads`
* `Audiobooks`
* `SD Card Photos`

Selecting an existing Local opens it for editing. Pressing Back while editing returns to the Locals list without saving changes.

### Remotes

Remotes are FTP or SFTP locations.

Each Remote contains:

* a friendly name,
* connection type,
* host/address,
* optional port,
* optional remote path,
* username,
* password.

The Remotes list shows the configured path and host beneath the Remote name.

Selecting an existing Remote opens it for editing. Pressing Back while editing returns to the Remotes list without saving changes.

## Adding a Local

Open the **Locals** tab and choose **New**.

Enter a unique Local name, then choose the folder.

The Android folder picker will appear. Select the folder Soneme Sync should use and confirm the selection.

Choose **Save**.

Local names only need to be unique among other Locals. A Local, Remote, and Job may all share the same name if that happens to be useful.

## Adding a Remote

Open the **Remotes** tab and choose **New**.

Choose either **FTP** or **SFTP**, then configure the connection.

### Address

The Address field accepts a normal hostname or IP address:

    nas.example.com

    192.168.1.10

It can also parse a full connection URI and populate the applicable fields:

    sftp://user:password@nas.example.com:2222/photos

The Connection Type selector remains authoritative. In other words, selecting SFTP means the Remote is SFTP even if the contents typed into Address happen to resemble some other URI scheme.

### Port

Port is optional.

If omitted:

* FTP uses port `21`
* SFTP uses port `22`

### Path

Path is optional.

Remote paths are used with the same absolute or relative meaning they normally have on the configured server.

For example:

    /srv/photos/phone

is passed as an absolute path, while:

    photos/phone

is passed as a relative path and is interpreted relative to whatever location the FTP or SFTP session normally uses.

Soneme Sync does not try to "fix" remote paths or convert one form into the other.

If Path is blank, the Remote uses the server/session's normal starting location.

### Authentication

SFTP requires a username and password. SSH keys are not supported.

FTP username and password are optional. If both are blank, Soneme Sync uses a conventional anonymous FTP login.

### Testing

Choose **Test** to verify a Remote before saving it.

The test:

1. resolves the host,
2. connects,
3. authenticates,
4. checks that the configured path can be listed,
5. disconnects.

The test does not create, delete, or modify files.

## Creating a Job

Open the **Jobs** tab and choose **New**.

Give the Job a name, then choose whether its source is Local or Remote.

Choose the source. The destination will automatically be the opposite type:

* Local source -> Remote destination
* Remote source -> Local destination

Choose the destination and save the Job.

If no suitable Locals or Remotes have been configured yet, Soneme Sync will require you to create them before creating the Job.

## Running a Job

Select a Job from the Jobs list.

Soneme Sync opens the Run screen, clears that Job's previous log, and begins the sync.

The current log is displayed while the Job runs. The sync continues in a foreground service if the screen turns off, the flip is closed, or the Run activity is no longer in the foreground.

A successful run displays its success state for about five seconds, then returns to the Jobs list.

If the Job fails, the error state remains on screen so the log can be reviewed.

The saved Job information screen also provides access to the most recent run log.

## What Actually Gets Synchronized

This is the most important part of Soneme Sync's behavior.

The source tree is recursively compared with the destination tree by **name only**.

File size, modification time, creation time, checksum, and file contents are not used to decide whether something should transfer.

If the source contains:

    DCIM/
      20260811_120000.jpg
      20260811_121500.jpg

and the destination already contains:

    DCIM/
      20260811_120000.jpg

then only:

    20260811_121500.jpg

is transferred.

Soneme Sync does not examine whether the existing `20260811_120000.jpg` is older, newer, larger, smaller, or completely different. The name exists, so it is left alone.

The same rule applies to directories.

This also means a file/folder type collision is simply skipped. If the source has a file named `Photos` and the destination already has a folder named `Photos`, then the destination already has something with that name and Soneme Sync wants nothing further to do with it.

Soneme Sync does **not** delete ordinary destination files that are absent from the source. A sync therefore adds missing names; it does not make the destination an exact mirror.

Dotfiles and dot-directories are included in normal recursive syncing.

## Partial Transfers

Soneme Sync avoids making an incomplete transfer look like a completed destination file.

While a file is being transferred, its destination name is temporarily changed to:

    .filename.soneme-part

After the transfer completes successfully, the temporary file is renamed to the intended filename.

If a transfer fails or is canceled, the partial file is deliberately left in place. This can be useful when diagnosing failures from the server or another machine.

At the beginning of the next run, Soneme Sync recursively removes stale `.soneme-part` files from the destination before beginning new transfers.

This is the only destination cleanup Soneme Sync performs automatically.

## Canceling a Job

Choose **Cancel**, or use Back while a Job is running, to stop it.

Soneme Sync interrupts the active transfer as promptly as the FTP/SFTP library permits, closes the connection, records the cancellation in the Job log, and returns from the run.

Any partial transfer is left in place and will be cleaned automatically at the beginning of the next run.

## Logs and Timeouts

Only the most recent log for each Job is retained.

Starting a new run discards that Job's previous log and begins a new one.

A Job is terminated with an error if:

* the log exceeds 2,000 lines, or
* there is no transfer/network activity for one minute.

This is intentionally a single-current-log model rather than an accumulating history.

## Navigation and Softkeys

Left and Right move between the Jobs, Locals, and Remotes tabs where applicable.

Up and Down move through lists and form controls.

Back generally moves to the previous logical screen. Back from an editor discards unsaved changes.

Soneme Sync uses the native three-position Sonim softkey bar. Available actions change with the current view and include operations such as:

* Info
* Edit
* Move up
* New
* Delete
* Test
* Save
* Run
* Cancel

## FTP and SFTP Notes

### FTP

FTP support is intentionally basic:

* plain FTP only,
* passive mode only,
* no TLS/FTPS.

Plain FTP does not encrypt credentials or transferred data. Use it only on networks where that is acceptable.

### SFTP

SFTP uses password authentication only. SSH keys are not supported.

Soneme Sync accepts the server's SSH host key without prompting or pinning it. This avoids failures when a server is replaced, reformatted, or otherwise receives a new host key, but it also means Soneme Sync does not provide SSH host-identity verification.

If you need strict host-key validation, this is not the SFTP client for that job.

## Storage and Privacy

Soneme Sync does not require:

* an account,
* Google Play Services,
* analytics,
* advertising,
* a subscription,
* a vendor cloud service.

Configuration, logs, and stored credentials are kept in the app's private storage.

Naturally, the app does require network access when running FTP or SFTP Jobs. Where those files go is entirely determined by the Remotes you configure.

## Building

Soneme Sync is a standard Gradle Android project.

A debug build can be produced with:

    ./gradlew assembleDebug

A configured release build can be produced with:

    ./gradlew assembleRelease

The resulting APK is written beneath:

    app/build/outputs/apk/

Release builds must be signed with an Android signing key before installation. Future updates must use the same signing identity as the installed release.
