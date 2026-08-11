# Soneme Sync

Multipurpose syncing solution for the Sonim XP3900 that allows the creation of FTP and SFTP sync jobs. Each sync job has a source and a destination. One must be local to the phone, and the other must be an FTP or SFTP endpoint and its associated address and login details. Sync jobs are run manually when desired.

# Target device properties

The Sonim XP3900 has the following constraints:

- 240x320
- Android 11 Go
- No touchscreen
- Options menu softkeys
- No Google Play Store or services
- App must be sideloaded as an .apk

# Application overview

Soneme Sync has a tabbed interface with three tabs: Jobs, Locals, and Remotes.

Jobs tab lists configurable Job items. Locals tab lists configurable Local items. Remotes tab lists configurable Remotes items.

A Job item consists of a uid, unique name, a source (a Local or a Remote item), and a destination (a Local or a Remote item). If the source is local, the destination must be remote. If the source is remote, the destination must be local. When attempting to create a Job, if no locals and/or remotes are available, the application should warn accordingly (e.g. "No local folders available", "No remote folders available", "No local or remote folders available") and refuse to pull up the Job Creation view.

A Local item consists of a uid, unique name, tree URI, and human-readable path on the local filesystem.

A Remote item consists of a uid, unique name, a connection type, an address, a port, a path, a username, and a password.

Unique names need only be unique within their class of item. A Local can be called "Photos" and a Remote can also be called "Photos" and a Job can also be called "Photos". Two of the same type cannot both be called "Photos", though. Names are case insensitive for determining uniqueness.

When transferring files, the decision of what to transfer is always based on file and folder names. Size, timestamp, etc. are not considered. If a destination already has a file or folder with the same name, the source's version should never be sent/created. File and folder name collisions are not handled uniquely--if a source has a file that matches a folder's name on the destination, then the destination already has something with that name and it is not eligible to transfer. If files and folder names ever collide, then the user messed up upstream and this app wants nothing to do with it.

To sync, determine the files and folders present in the source and destination and compare. Sync jobs recursively traverse the entire source tree. Any files and folders that the source has and the destination does not should be transferred to/created on the destination (including dotfiles). Any .soneme-part files present anywhere in the destination tree should be deleted before transfers begin.

To prevent partial transfers, when syncing files to the destination, they should have a . prepended and .soneme-part appended to their destination filename. On transfer completion per file they should be renamed back to their original name.

Auth is password only. This application does not support keys. App-private storage of credentials is fine to keep things simple. If someone steals my phone, manages to retrieve my passwords out of my device, hacks my wifi, and gets into my NAS, frankly at that point they earned the cat photos I'd have happily sent them anyway if they just asked.

SFTP host-key policy is accept anything. I change my servers out often enough that I'd be annoyed if swapping hardware or reformatting equipment caused errors in a random phone sync app.

FTP is plain FTP, passive mode only. If an FTP Remote's username and password are blank, assume conventional anonymous login.

Remote path semantics: Treat configured paths exactly as the underlying FTP or SFTP service would normally interpret them. Do not convert between absolute and relative paths or otherwise rewrite path semantics. If the user enters an absolute path, use it as absolute; if they enter a relative path, pass it through as relative and allow the server to resolve it according to that protocol/session’s normal behavior.

App starts up in Jobs tab.

# Views

## Jobs

### Controls

 - Left and right buttons switch tabs
 - Up and down cycle through the list
 - Clicking a job runs the job in Job Run view
 - Back button returns to the launcher

### Main content

Listing of jobs that can be run or edited

Each list item consists of:
 - Job name (marquee if out of room)
 - Last run date and time
 - Last run success/error/canceled indicator

### Options menu

 - Info

   Opens Job view
   
 - Move up

   Reorders the Job in the list up one spot. Blank menu slot if already the first item in the list.

 - New

   Opens Job Creator view


## Locals

### Controls

 - Left and right buttons switch tabs
 - Back button goes to Jobs tab
 - Up and down cycle through the list
 - Clicking a Local opens up Local Creator view with item fields populated with this Local

### Main content

Listing of Locals.
Local items have name on top line, and folder path below it. Both lines are capable of marqueeing when focused if they extend past the viewable area.

### Options menu

 - Edit
   
   Opens the Local Creator view with populated fields for the focused item.

 - Move up

   Reorders the Local in the list up one spot.  Blank menu slot if already the first item in the list.

 - New

   Opens the Local Creator view with blank fields


## Remotes

### Controls

 - Left button returns to Locals tab, right button does nothing
 - Back button goes to Locals tab
 - Up and down cycle through the list
 - Clicking a Remote opens up Remote Creator view with item fields populated with this Remote

### Main content

Listing of Remotes.
Remote items have name on top line, and "<path> on <URL>" below it. Both lines are capable of marqueeing when focused if they extend past the viewable area.

### Options menu

 - Edit
   
   Opens the Remote Creator view with populated fields for the focused item.

 - Move up

   Reorders the Remote in the list up one spot.  Blank menu slot if already the first item in the list.

 - New

   Opens the Remote Creator view with blank fields


## Job

### Controls

- D-pad scrolls
- Back button returns to Jobs view

### Main content

- Job name heading
- Job name
- Source heading
- Source name
- Destination heading
- Destination name
- Last run heading
- Last run date and time
- Log heading
- Log/error info from last run. Focusable box, non-editable, pre-scrolled to bottom.

### Options menu

 - Delete

   Deletes the job

 - Edit

   Opens job in Job Creator view

 - Run

   Runs job in Job Run view


## Job Creator

### Controls

- D-pad navigates fields
- Back button returns to Jobs view without saving

### Main content

- Job name field (warn if not unique among Jobs)
- Source heading
- Source type selector, options Local and Remote
- "Choose source..." selector. If Local is chosen, names of Locals are brought up to select one. If Remote chosen, then names of Remotes
- Destination heading
- "Choose destination..." selector. If source was Local, show Remotes. If source was Remote, show Locals.

### Options menu

 - (blank)

 - (blank)

 - Save

   Save only appears if Job name, source, and destination have values and the Job name is unique.


## Job Run

### Controls

- Back button terminates job and returns to Jobs view

### Main content

Foreground service. Continue if the flip is closed / screen goes off / user leaves the Activity in this view. Notification of "Soneme Sync — Running <job name>...".

Job Run main content view will be the trickiest visually. It is meant to fill the screen without scrolling. Some adjustments may be necessary after testing.

On start:

Discard job's previous log and begin recording new log.

- Loading icon
- "Running <job name>..."
- Log box with auto scrolling log. Not focusable. Logs can be reviewed later in Job view.

If log exceeds 2000 lines, throw an error condition and add "Maximum log length exceeded" to the end of the log.
If no transfer/network activity for 1 minute, throw an error condition and add "Job terminated by timeout" to the end of the log.

On success:

- Success icon
- "<job name> success."
- Log box remains in place

Success message stays for 2 seconds then user is returned to Jobs view

On error:

- Error icon
- "<job name> failed."
- Log box takes focus and becomes scrollable

Error message stays in place until user hits back button.

### Options menu

 - Cancel

   Stop after/interrupt the currently active transfer as promptly as the protocol library permits, close connection, record Canceled on end of log, retain the log generated to that point. Do not clean up partial transfers.

 - (blank)

 - (blank)


## Local Creator

### Controls

- D-pad navigates fields
- Back button returns to Locals view without saving

### Main content

- Local name field (warn if not unique among Locals)
- Folder heading
- "Choose folder..." selector.

### Options menu

 - Delete

   Delete only appears if arriving from the Edit softkey or clicking an existing job in the Locals view. If Local is used by a job, refuse to delete with message "Unable to delete. Local <name> is used by job <name>."

 - (blank)

 - Save

   Save only appears if Local name and folder have values and the Local name is unique.


## Remote Creator

### Controls

- D-pad navigates fields
- Back button returns to Remotes view without saving

### Main content

- Remote name field (warn if not unique among Remotes)
- Connection type label and selector, options FTP and SFTP
- Address label and field, parses address and keeps host, populates subsequent fields with port, path, and authentication info if present.
- Port label and field, optional. Defaults to 21 if FTP and not filled, 22 if SFTP and not filled.
- Path label and field, optional. Accepts absolute or relative remote paths. Preserve the path semantics as entered and pass it to the FTP/SFTP implementation without converting between absolute and relative forms. Perform only whatever escaping or encoding is required by the protocol/library. If no path specified, then it's just wherever the server dumps them.
- Authentication heading
- Username label and field, optional if FTP, required if SFTP
- Password label and field, optional if FTP, required if SFTP

### Options menu

 - Delete

   Delete only appears if arriving from the Edit softkey or clicking an existing job in the Remotes view. If Remote is used by a job, refuse to delete with message "Unable to delete. Remote <name> is used by job <name>."

 - Test

   If all fields valid, test the connection and report success or failure. To test, resolve host, connect, authenticate, verify configured path exists and can be listed, disconnect. No filesystem changes

 - Save

   Save only appears if Remote name and address have values and the Remote name is unique.
