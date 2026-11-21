# Changelog

## 1.1.1.3

- Fixed: the whole Today list could vanish during the day while you were ticking tasks or putting them on Today. The daily reset now only takes off tasks put on Today before midnight, and it always finishes once started, so running it again (an interrupted start, or tasks synced in from another device) keeps today's picks
- Desktop: when Maple Mono NL NF CN is installed, all text uses it, so Chinese and Latin share one set of glyphs

## 1.1.1.2

- New Windows desktop app. It runs the same interface with a wide layout and keeps its own copy of your tasks in the GritGlass folder under %APPDATA%. Ctrl+N adds a task, Esc goes back, and the window remembers its size and place
- On a wide window the task wall is now a free desk: tasks lie on warm paper as notes you can drag anywhere, slightly tilted, straightening and lifting under the pointer. Size shows importance (high priority or a passed deadline gets the largest card); the paper deepens from plain to caramel as the deadline nears. Pan by dragging the desk, the middle button or the wheel; zoom with Ctrl + wheel or a pinch. Double-click a card to edit it
- Desk tools: Scatter, By category and Urgent to centre lay all cards out again (with Undo), and Fit frames them. New tasks find a free spot near the middle by themselves. Card positions stay on each device
- On the phone, Overview shows Due instead of the wall: tasks with a deadline, soonest first, grouped by how close it is, with the same paper tones
- LAN Sync (Setting → Backup): turn it on in the desktop app, scan its QR code or type its address, port and PIN on the phone, compare both sides, then push the phone's tasks to the computer or pull the computer's onto the phone. It stays inside your network and stops after 10 minutes without use or 5 wrong PINs
- Before sync or a restore replaces a device's tasks, a restore point is saved on that device; the last 5 can be restored from the LAN Sync page
- Settings: the three day limits now mark when a deadline is due, soon and upcoming (desk paper and Due groups); the text size applies to all cards

## 1.1.1.1

- Task wall & cards: removed the 48–68% title colour fade on normal/low priority cards and added perceived-luminance contrast clamping so light user-chosen text colours stay legible against warm beige paper (stored colours are unchanged)
- Category trays: chip labels now stay in high-contrast neutral ink with the category colour shown by the leading dot
- Consistent warm tones: explicit warm error and container colours in the theme, onSurface instead of pure black for unset section colours, and removal of leftover high-saturation tokens
- Text truncation & spacing: ellipsis overflow and compact padding fallback in segmented controls, ellipsis on task descriptions, expanded sections, and category titles, secondary label styling for the section toggle, and disabled Android extra font padding on card titles
- Archive toast: raised above the floating add button and card tap keys on narrow screens
- Unified concentric geometry and tactile cards: 28 dp concentric toolbar corners, 12 dp / 16 dp card radii, consistent popup list corners, pure circular/capsule controls, raised warm-umber paper elevation on active wall cards (HIGH lifted further) and recessed top inner shadow on sunken cards
- Settings & top bars: lighter regular-weight subtitles on top bars, cleaner day-threshold inputs without "days" suffixes, and a grouped clickable font list under Look and Feel

## 1.1.0

- New Overview tab between Task and Setting, with three views: the task wall, Later and Done
- Task wall: every open task is a card. The closer its deadline, the larger the card; the lower its priority, the fainter its title and border. Cards are read from the top left: most urgent first, then most important
- Tasks have a deadline (a day, optional) and a priority (low, normal, high); set both in the task editor. The Task page shows the deadline under the title
- Two glass keys over the wall choose what a tap on a card does: Preview opens a pane of glass over the wall where you can tick sections, finish, suspend or put off the task; Detail opens the editor. A long press does the other one
- A task whose deadline has passed keeps the largest card with a thin mark on the left, and offers three choices on the card: set a new deadline, remove the deadline, or give it up
- Tasks without a deadline sink down the wall the longer they are left alone. After 30 days (low priority) or 60 days (normal) the preview asks whether to make the task smaller, put it under Later, or give it up. High priority tasks never sink
- Completing a task archives it at once: it leaves the Task page and the wall, and "Archived · Undo" appears for a few seconds. Archived tasks are listed under Done, newest first, and return to their old place when reopened. "Delete completed" moved there
- Suspend a task until tomorrow, next Monday, a month from now or a day you pick; it stays off the Task page and the wall until then. "Later" parks a task with no date
- Today starts empty each day: tasks left on it from the day before go back to their categories
- Settings: the three day limits that decide card size, the wall text size, and whether priority also tints the card background
- Sections are simply done or not done; their completion date is gone. The "hide completed" option of categories and the "reorder after completing" setting are removed, since completed tasks are always archived
- The tasks widget no longer shows completed tasks
- All of the app's own text is English now; labels that were Chinese in 1.0.x have been translated
- Backups are now in Grit Glass's own format (version 2). Backups from Grit Glass 1.0.x and from Grhinet can still be imported; Grhinet cannot import backups made by this version

## 1.0.2

- Tasks: tapping a task no longer marks it done or not done by accident. Turn on the new tick button beside Reorder first; while it is on, each task shows a circle and a tap toggles it. With it off, a tap on a task does nothing and the list just scrolls
- The tick button and Reorder turn each other off, and both turn off when you switch category

## 1.0.1

- Tasks widget: each task shows only its title; the step count is gone, and a task without steps no longer shows another task's step line
- Tasks widget: task text always uses the text size chosen in the app, without jumping to another size when the list redraws

## 1.0.0

- First release of Grit Glass: the Grhinet to-do app without the Rhine view and task pictures; it installs alongside Grhinet and imports Grhinet backups
- Buttons are clear liquid glass that bends what scrolls beneath it, with a slight colour fringe at the rim (Android 13 and later)
- Pressing a button fills it with light instead of darkening it
- Every button, category chip and tab is the same size; Add task is a larger clear glass lens instead of a dark button, and Save and Done are glass too
- The bottom bar floats over the page, and lists and editors scroll on beneath it
- Switches turn olive when on
