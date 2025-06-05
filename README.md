Java Thread Dump Analyzer with Web and CLI Interfaces

Design Document: Java Thread Dump Analyzer Tool
Introduction and Objectives
Java thread dumps are snapshots of all threads running in a JVM at a given instant. Each thread’s state is listed along with its stack trace, providing insight into what the application and JVM threads are doing
reflectoring.io
. A thread dump is a vital diagnostic tool for identifying performance bottlenecks (e.g. high CPU threads), unresponsive applications, and deadlock situations
reflectoring.io
. This design outlines a Java-based Thread Dump Analyzer that can be used by engineers to easily analyze such thread dumps and pinpoint issues. The primary objectives for this tool are:
Zero Dependencies: The analyzer should run as a standalone JAR with no external prerequisites (beyond a Java runtime). Users can simply run it without installing databases or app servers.
Dual Interface (CLI & Web): Provide both a command-line interface (for scripted or console use) and a user-friendly HTML Web UI (served via an embedded web server) for interactive analysis.
Multi-Format Support: Handle a wide range of thread dump formats – including standard HotSpot dumps (from jstack, jcmd or kill -3), hs_err_pid.log crash files, and IBM/OpenJ9 javacore dumps – and potentially even JSON-based dumps from monitoring tools. The tool must recognize and parse these formats automatically.
Analysis & Visualization: Generate meaningful statistics and visualizations from the thread data. This includes computing how many threads are in each state (RUNNABLE, BLOCKED, WAITING, etc.), identifying thread state distribution over time (when multiple dumps are provided), highlighting stack trace “hotspots” (e.g. common methods or locks where many threads converge), and detecting deadlocks or other synchronization issues. Results should be presented in tables, charts (like histograms or pie charts), and summaries for easy understanding.
Contextual Explanations: The Web UI should assist users in interpreting the data by providing context-sensitive help. For example, if a thread is in WAITING state or blocked on a lock, the UI can offer explanations or tooltips about what that means (pulled from known definitions or documentation)
docs.oracle.com
. Every part of the thread dump (thread states, stack frames, lock identifiers, etc.) should be accompanied by UI hints or documentation to educate users as they browse.
Multi-Dump Comparison: Allow users to load multiple thread dumps (e.g. a series of dumps taken over time) and compare them. This feature helps in analyzing how thread states evolve – for instance, to spot threads that are consistently stuck, to see growth in certain thread pools, or to catch deadlocks that occur after a certain point. The tool should present side-by-side comparisons or timeline views of thread state distributions and highlight differences between dumps (new threads, disappeared threads, changed states, etc.).
Report Exporting: Support exporting the analysis results into shareable report formats such as PDF (for a printable report), CSV (for raw data export like thread lists or statistics), and standalone HTML (a self-contained report that can be viewed in a browser without running the tool).
By meeting these objectives, the tool will enable comprehensive thread dump analysis with minimal setup, catering both to quick command-line use and in-depth visual exploration via a web browser.
System Architecture Overview
The Thread Dump Analyzer is designed with a modular architecture, separating concerns into distinct layers. The core components include a Parsing layer (to read various thread dump formats), an Analysis layer (to compute statistics and detect issues), a Data Model (to represent threads and dumps in memory), and a Presentation layer which encompasses both the CLI output and Web UI. The diagram below outlines the high-level architecture:
Input (Thread Dump files) → Parser Module (per format) → In-Memory Data Model → Analysis Engine → Outputs (CLI Report or Web UI).
Each part of the system is designed for extensibility — for example, adding support for a new dump format would mean adding a new parser implementation without altering the analysis logic. The table below summarizes the major modules and their responsibilities:
Module/Component	Responsibility
Thread Dump Parsers	Parse raw thread dump files of various formats (HotSpot, OpenJ9, etc.) into a structured internal model. Includes sub-parsers for each supported format.
Data Model	In-memory representation of thread dump data (threads, stack traces, locks, etc.), serving as a common input for analysis and output generation.
Analysis Engine	Processes Data Model to compute statistics (thread counts, state distribution, hotspots), detect deadlocks/livelocks, and prepare data for visualization.
Web UI (Embedded Server)	Presents an interactive HTML interface for analysis results. Handles file uploads, renders summary charts/tables, provides navigation through threads, and shows context help. Runs on an embedded HTTP server (e.g. Jetty) so users can access it via a browser with no separate deployment.
CLI Interface	Provides a command-line experience. Accepts input files and options, invokes parsers and analysis, and outputs results to console or files (supporting text, HTML, PDF, CSV outputs as specified by user).
Reporting/Export Module	Utilities to generate reports in various formats (HTML/PDF/CSV) from the analysis results, ensuring that both CLI and Web UI can export consistent reports.
All these components interact such that the Parsing layer feeds the Data Model, which is then examined by the Analysis layer. The results can be rendered either as textual output (CLI) or as rich visual output (Web UI), using the same underlying data and analysis.
Parsing Layer: Multi-Format Thread Dump Support
One of the key challenges is supporting the numerous thread dump formats produced by different JVMs and tools. The Parsing layer consists of a framework of parser classes that can ingest a dump file (often a plain text file) and produce a structured representation of the threads and their details. The design will include an abstract ThreadDumpParser interface or class, with concrete implementations such as HotSpotParser, OpenJ9Parser, etc. A factory or format detector will choose the appropriate parser by examining the file content (for example, looking for specific keywords or patterns unique to each format). Supported Formats: The tool will handle at least the following formats from the start:
HotSpot JVM thread dumps: These come from OpenJDK/Oracle JVMs via tools like jstack, jcmd Thread.print, or by sending SIGQUIT/Ctrl+Break (kill -3) to the process. They are textual dumps where each thread is listed with a header line (including the thread name, priority, tid, nid, state) followed by its stack frames and lock info. The parser will identify thread boundaries (often demarcated by a line starting with "Thread Name" or a line that includes tid=/nid=) and extract details.
HotSpot Error Logs (hs_err_pid.log):* These crash logs contain a wealth of information, including a section listing all threads and their states at the moment of crash. The parser will focus on the “All Threads” section of hs_err files
blog.fastthread.io
, extracting each thread’s stack trace and state. hs_err files also include native thread information and memory info, which might be parsed or skipped according to the tool’s focus. Support for hs_err files means users can analyze post-mortem crash data just like a live thread dump
fastthread.io
.
IBM/Eclipse OpenJ9 javacore dumps: OpenJ9 (formerly IBM J9) produces thread dumps in a structured text format often called javacore (e.g. files named javacore.DATE.TIME.pid.txt). These have a different syntax – threads are listed under sections with identifiers like 1XMTHDINFO, 3XMTHREADINFO etc., and thread states might be denoted by abbreviations (e.g., R for runnable, CW for conditional wait). The OpenJ9Parser will recognize these tokens and parse accordingly. It will convert IBM-specific state codes to the standard Java Thread.State where possible. For example, older IBM dumps might label certain threads as Conditional Wait (CW) which correspond to a thread that is actually waiting (or was runnable but waiting on a condition)
publib.boulder.ibm.com
. The parser will map those to a WAITING or BLOCKED state with additional info as needed. By supporting javacore dumps, the tool covers OpenJ9 and IBM JDK users
fastthread.io
.
Other formats: The architecture anticipates that some tools or APMs output thread dumps in JSON or other structured formats. As noted by prior art, thread dump format can vary by Java version, JVM vendor, and tooling (even JSON in some cases)
fastthread.io
. The parser layer can be extended with new implementations to handle these. For instance, a JsonThreadDumpParser could be added to handle dumps from a monitoring system that exports threads in JSON.
Parsing Strategy: Each parser will likely use pattern matching and state machines to read the file. For example, the HotSpot parser might use regex or string searches to find lines that mark the beginning of a new thread’s stack (identifiable by the quote " starting a thread name, or known keywords like daemon prio=). It will then capture subsequent lines until the next thread or end-of-dump. During parsing, relevant data to extract includes:
Thread name (e.g. "http-nio-8080-exec-10"),
Thread ID (sometimes a native ID or Java ID),
Thread state (e.g. RUNNABLE, WAITING on condition, TIMED_WAITING (on a monitor), BLOCKED on lock, etc.),
Stack trace elements (each stack frame with class, method, file, line, and maybe native line info),
Locked monitors and owned synchronizers (HotSpot lists these under each stack if -l option is used or in dumps by default),
The object or lock a thread is waiting on (if BLOCKED or WAITING on a monitor, the dump usually shows a line like “- waiting to lock <0x...> (a java.lang.Object)” which should be captured and linked to the thread holding that lock if possible).
For OpenJ9 dumps, the parser will read the formatted sections:
e.g. Thread Details: look for sections like 3XMTHREADINFO which contain thread ID, name, state (java/lang/Thread state: ...), native ID, etc.
Stack frames: denoted by identifiers like 4XESTACK lines for each frame.
Locking information: sections like 3XMLOCKINFO for contended locks, etc.
The parser will need to assemble this structured data into the common Data Model (described next).
To keep the tool extensible, adding a new parser does not affect other components – it simply needs to produce the data in the expected format. The parsing is synchronous and typically fast (thread dumps are text on the order of kilobytes to a few MB at most). Even so, the design will allow parsing in a background thread or concurrently (especially if the Web UI accepts multiple files at once) to keep the interface responsive.
In-Memory Data Model
Once a thread dump file is parsed, the information is stored in an in-memory Data Model that represents the threads, locks, and other relevant data. This model acts as a bridge between parsing and analysis, and it ensures the CLI and Web UI are using the same representation of the data. Core Classes and Structures: The data model can be object-oriented, roughly mapping to the following conceptual classes:
ThreadDump: Represents a single thread dump snapshot. Contains metadata like a timestamp (if known – e.g., the time the dump was taken or found in the file), an optional identifier (file name or user-given label), and a collection of threads. If the dump file provides JVM specifics (e.g. JVM version, uptime, etc.), those could be stored here as metadata as well.
ThreadInfo: Represents an individual thread within a dump. Key fields include:
threadId: a numeric identifier (for HotSpot this could be the native thread ID nid or the tid if available; for OpenJ9 the thread ID from javacore).
threadName: the name of the thread (as given in the dump).
threadState: the thread’s state (mapped to the standard java.lang.Thread.State enum if possible, e.g. NEW, RUNNABLE, BLOCKED, WAITING, TIMED_WAITING, TERMINATED). If the dump provides more nuanced states (like OpenJ9’s additional codes), those can be captured in additional attributes (e.g. an OpenJ9-specific state reason).
stackTrace: an ordered list of StackFrame objects representing the stack trace. Each StackFrame may include the class and method name, file and line number, and possibly a flag if it’s a native or JIT-compiled frame.
lockedMonitors: a list of LockInfo objects representing monitors the thread currently owns (for HotSpot, lines like “- locked <0x12345> (a SomeClass)”).
waitingOn: a LockInfo object if the thread is blocked or waiting on a monitor/lock (e.g. “waiting to lock <0x12345>”), indicating the lock it is trying to acquire.
lockedSynchronizers: (for HotSpot) any java.util.concurrent locks held (listed under “Locked ownable synchronizers” in the dump).
daemon, priority, etc.: Additional attributes as available (whether thread is daemon, OS priority, etc., as shown in some dumps).
LockInfo: Represents a lock or monitor identified in the dump (typically by an identity hash or address like 0x12345 and type). It may include:
id: an identifier (e.g., the memory address or unique hash code of the lock object).
className: the type of the lock object (e.g., java.lang.Object or a specific class if known).
ownerThread: (optional) reference to the ThreadInfo that currently holds this lock, if known. This can be populated during parsing or later analysis by cross-referencing threads that are holding versus waiting on locks.
All these are in-memory Java objects. For efficiency, cross-links can be set up (for example, each ThreadInfo waiting on a lock can have a reference to the ThreadInfo that owns that lock, if that thread is in the same dump). This makes analysis like deadlock detection easier. When multiple dumps are loaded (for comparison), the data model might hold them in a collection:
e.g., a ThreadDumpSeries object that contains multiple ThreadDump instances (with perhaps an overall label or ordering). However, it’s also feasible to just use a List<ThreadDump> in the analysis logic without a special container class. For clarity, the design can include a higher-level model like AnalysisSession that encapsulates either a single dump or a set of dumps being analyzed together.
Memory Considerations: Thread dumps are generally not huge (perhaps tens of thousands of lines at most if hundreds of threads), so representing them in memory is not too heavy. If analyzing many dumps at once, memory usage scales linearly. The design assumes this is acceptable for typical use (e.g., comparing 5-10 dumps). If needed, the tool could allow dumping older ThreadDump objects from memory once analysis is done, to conserve memory, but usually this isn’t necessary unless extremely large dumps or many dumps are used. The Data Model is crucial for decoupling input parsing from output generation. It provides a consistent API to query threads, which the Analysis Engine uses next.
Analysis Engine and Statistics Computation
Once the data model is populated for one or more thread dumps, the Analysis Engine runs a series of computations to derive insights. This module operates on the ThreadDump and ThreadInfo objects rather than the raw text, making it easier to compute metrics. Key functions of the Analysis Engine include:
Thread State Statistics: Tally the number of threads in each state for a single dump. For example, count how many are RUNNABLE, BLOCKED, WAITING, etc. This yields a distribution that can be presented as a summary table or chart (e.g., a pie chart of thread states). If multiple dumps are loaded, the engine can produce a state distribution for each dump and even a timeline of how these counts change over time.
Thread Categorization: Identify categories of threads (if possible) such as JVM internal threads vs application threads (perhaps by name patterns like those starting with “GC” or “Attach Listener” could be recognized as JVM threads). This can be used to filter or group threads in the UI.
Deadlock Detection: Analyze locks and waiting threads to detect deadlocks. HotSpot thread dumps explicitly list deadlocks at the end of the dump if any are found (“Found one Java-level deadlock…” along with the threads involved). The tool will capture that if present and mark those threads as deadlocked. Additionally, the engine can perform its own graph cycle detection: build a wait-for graph where nodes are threads and edges represent “Thread A waits on lock L held by Thread B”. By traversing this graph, any cycle indicates a deadlock. The engine would then identify all threads in the cycle and the locks involved. These can be reported clearly (e.g., “Threads T1 and T2 are deadlocked on locks L1 and L2”). This way, even in formats or cases where deadlocks are not pre-detected by the JVM (or if they involve more than two threads), the tool can still inform the user.
Lock Contention Analysis: Beyond deadlocks, the tool can identify heavily contended locks or hotspots. For example, if many threads are blocked on the same lock (many ThreadInfo objects have waitingOn the same LockInfo), that lock is a bottleneck. The analysis engine can flag this and report which lock and how many threads are waiting for it, and which thread holds it.
Stack Trace “Hotspots”: Determine if certain stack frames or methods are appearing frequently across many threads. For instance, if 50 threads are all showing java.util.Thread.sleep in their stack or are all in a particular method of the application, that method might be a hotspot or a point of contention. The engine could aggregate stack trace data to find the top N methods or classes that appear in the thread dump. This is analogous to finding common patterns – e.g., many threads waiting on java.sql.Connection.await might indicate a database connection pool exhaustion. These findings can be presented as a list of “common stack trace locations” or even as a flame graph visualization (collapsing identical stacks) in the UI. (In CLI mode, a textual summary of hottest stack frames could be shown.)
Thread grouping and naming patterns: The engine might group threads with identical stack traces
blog.ycrash.io
. This is useful to collapse duplicate information in the UI (e.g., show one stack trace and indicate “20 threads have this same stack trace” – often the case for thread pools where many threads are idle in the same place). It can also group by thread name patterns (for example, all “http-nio-8080-exec-*” threads can be recognized as a thread pool group). This grouping can make the analysis more digestible by not overwhelming the user with very similar threads.
CPU/Usage Indicators: Although a static thread dump does not directly show CPU usage, if the tool is given multiple dumps or additional info, it could infer some usage. For instance, a thread consistently appearing as RUNNABLE in every dump in the same stack location might be a busy loop consuming CPU. If integrated with an optional top -H snapshot (which lists thread IDs with CPU), the tool could even merge that info. However, at minimum, the engine can flag threads that are RUNNABLE in many dumps as likely CPU consumers.
Timeline / Evolution Analysis (for multiple dumps): When multiple dumps are loaded (assumed to be sequential snapshots of the same JVM over time), the engine compares them to find trends:
It can track a particular thread across dumps by thread ID or name. If a thread with the same ID appears in dump1 and dump2, it’s the same live thread. We can then see if its state changed, or if its stack trace progressed.
Threads that are new in later dumps (absent in earlier) can be identified (perhaps a new thread was created).
Threads that disappeared (present in earlier, gone in later) can indicate threads that finished execution.
By aligning threads, the engine can produce a comparative summary
blog.ycrash.io
. For example, a table of specific important threads showing their state in each dump side-by-side (highlighting if a thread went from RUNNABLE to BLOCKED, etc.). This is useful to spot patterns like a thread stuck in WAITING state throughout, or increasing numbers of threads in a certain pool.
Also, overall metrics over time: e.g., a chart of total thread count over dumps, or how RUNNABLE vs WAITING counts changed, could be produced.
The image below illustrates a comparative view of thread states across two dumps in a prior art tool – each row is a thread, and columns show its state in each dump (green = RUNNABLE, orange = WAITING, etc.), making it easy to spot changes or consistently stuck threads:
Example visualization of comparing two thread dumps, showing each thread’s state in Dump1 vs Dump2. This helps identify threads that remain stuck or change state over time (green = RUNNABLE, orange = WAITING, etc.), fulfilling the multi-dump analysis requirement.
Advisories and Patterns (Extensible): While not explicitly required, the design allows adding heuristic checks or “advisories.” For example, the analyzer might detect a thread pool starvation scenario or a particular deadlock pattern and provide a short description/advice. (This idea is inspired by existing tools that incorporate expert knowledge patterns
github.com
.) The architecture could include an advisory engine where rules can be added to flag known thread anti-patterns (e.g., too many threads in blocked state might indicate a concurrency bottleneck).
The Analysis Engine outputs data structures that represent these findings – e.g., a map of state -> count, lists of DeadlockInfo objects (if any deadlocks found, listing the threads and locks in cycle), a list of HotspotFrames (methods with high frequency counts), etc. These will then be used by the UI or CLI to present results to the user. By separating this logic, we ensure that the analysis can be invoked the same way from both interfaces and any heavy computation is done in one place.
User Interface – Web Application
The HTML/Web user interface is a crucial part of making the tool “user-friendly.” It will run on an embedded web server (such as Jetty or a similar lightweight Java HTTP server) launched by the application, so that when the user starts the tool (e.g., by running the JAR), it opens up a port (configurable, default say 8080) where the UI can be accessed via a browser. This approach means zero manual setup – no need to deploy a webapp to an external server; the server comes up automatically within the application process. Key aspects of the Web UI design:
Landing/Upload Page: When accessed initially, the web interface will present an upload form where users can select one or multiple thread dump files from their system. They can then start the analysis. The UI should allow multiple files to be uploaded at once (to support the comparison feature). For convenience, it may also list recently analyzed files or allow the user to paste raw thread dump text directly into a text area as an alternative input method.
Processing & Storage: Upon file upload, the server-side will invoke the parsing and analysis on those files. The results (the data models and analysis outputs) can be stored in memory (perhaps cached in the server session or a singleton since this is a single-user desktop tool typically). If multiple analysis sessions are needed, the tool could allow managing them, but initially focusing on one set of dumps at a time is fine. Once processed, the UI will navigate to the Results Dashboard.
Results Dashboard: This is the main view showing analysis of the loaded dump(s). It will include several sections:
Overview Summary: A high-level summary of the dump or dumps. For a single dump, this could be a line like “Total Threads: X; Runnable: Y, Blocked: Z, Waiting: W, Timed_Waiting: T, Terminated: U”. If multiple dumps, it could summarize each or highlight the differences (e.g., “Dump1 vs Dump2: total threads increased from 50 to 80, runnable threads from 10 to 5, etc.”).
State Distribution Chart: A visual chart (pie or bar chart) showing the proportion of threads in each state for the current dump. This helps quickly see if the majority are, say, waiting or runnable. For multiple dumps, the UI might show multiple pie charts side by side or a stacked bar chart per dump. Example: the UI could display a pie chart where slices represent RUNNABLE, BLOCKED, WAITING counts, so one can see at a glance how threads are divided. Below is an example of a summary view from an existing tool, illustrating how thread counts by state might be visualized:
Sample thread state summary visualization for a single dump (pie chart showing distribution of threads by state). Our tool will present similar histograms or charts to convey how many threads are RUNNABLE, BLOCKED, WAITING, etc., fulfilling the requirement for meaningful visual summaries.
blog.ycrash.io
Threads Table/List: A detailed list of threads, which can be initially grouped or filtered. For instance, threads could be grouped by state or by name pattern. The user can expand a group to see individual threads. Each thread entry in the list might show: Name, ID, State, and possibly the first stack frame or an important marker (like “waiting on X lock” or “at Y method…”). This list should be sortable and searchable (e.g., filter by thread name).
Stack Trace Viewer: When a user clicks on a specific thread from the list, the UI should display the full stack trace of that thread. This can be on the same page (e.g., expanding below the thread in the list) or a separate pane. The stack trace view should highlight lines that are of interest: for example, the line where it’s blocked or waiting (and indicate which lock it’s waiting on, possibly linking to the thread that holds that lock). If the thread is in a deadlock, that could be indicated in red, etc.
Analysis Highlights: Apart from raw thread data, the UI can have a section for “Notable Findings” which surfaces key points from the analysis engine. For example:
“Deadlock detected involving 2 threads (Thread-A and Thread-B).” This can be shown prominently, with an option to jump to those threads or to a deadlock details view.
“5 threads are waiting on the same lock (com.example.Foo$Bar lock) held by Thread-X – possible bottleneck.”
“All 20 worker threads have identical stack traces (they are all in Object.wait() on a task queue)
blog.ycrash.io
.” This tells the user that those threads might be idle waiting for work.
“Thread Y appears in RUNNABLE state across all 3 dumps, at the same code location – possible hot loop.”
These insights make the tool more than just a data viewer, but an analytical assistant.
Comparison View (if multiple dumps): When multiple dumps are loaded, the UI should provide a way to compare them. This could be a dedicated tab or section. For example:
A timeline chart could illustrate the count of each thread state across dumps (x-axis: time or dump sequence, y-axis: number of threads in a given state, with multiple lines or areas for each state). This shows trends (e.g., gradually rising blocked threads).
A comparative threads table: as mentioned, list key threads and how their state changed dump-to-dump. Possibly the UI allows selecting two specific dumps to diff. Or a play-through where you can click “next dump” and see the changes highlighted.
The UI can also simply list threads that are new or gone between dumps.
Given the complexity, an initial approach is to highlight differences in textual form (e.g., “New threads in Dump2: X, Y; Threads terminated by Dump2: Z; Threads that changed state: A from WAITING to RUNNABLE”). This textual summary, along with charts, addresses the evolving state analysis requirement.
Context-Sensitive Help: To make the UI educational, certain terms and sections will have explanatory pop-ups or links. For example:
Hovering over a thread State (like “TIMED_WAITING”) could show a tooltip: “TIMED_WAITING: The thread is waiting for an action for up to a specified time
docs.oracle.com
.” Similarly, hovering over “BLOCKED” might show “BLOCKED: The thread is blocked waiting for a monitor lock
docs.oracle.com
.” These descriptions can be sourced from official Java documentation.
If a thread is shown as daemon, that term could have a tooltip explaining what a daemon thread is.
The UI might include an integrated help panel or info icons next to sections like the charts or deadlock reports, explaining how to interpret them. For instance, an info icon next to the state distribution chart can explain the thread states briefly, or next to a deadlock warning can explain what a deadlock means.
A small “Legend” section can clarify color codes used in charts or comparison views (e.g., what each color or symbol stands for).
Technology Stack (UI): The Web UI will be generated by the Java application. There are a couple of approaches:
Server-Side Rendered: The application could use a Java templating engine (like FreeMarker, Thymeleaf, or JSP with Jetty) to produce HTML pages populated with the analysis data. This would result in mostly static pages (with maybe some inline JavaScript for minor interactions).
Single Page Application: Alternatively, the embedded server could serve a static front-end (HTML/JavaScript) and expose REST endpoints that provide the analysis data in JSON. For example, after upload, the browser could load a page that uses AJAX to fetch “/api/summary” or “/api/threads” etc. This would allow a more dynamic experience (e.g., filtering/sorting in the browser). A lightweight JS framework or even vanilla JS with a library like Chart.js for charts could be used.
Given the requirement of simplicity, a hybrid approach might be simplest: generate an HTML report with embedded charts (possibly using a JS chart library but pre-feeding it data). However, for interactivity like expanding threads and tooltips, some JavaScript is needed. The design leans towards using popular libraries:
Embedded Server: Jetty (as suggested) or an alternative like Spring Boot with an embedded Tomcat. Jetty is lightweight and can be programmatically started on a port, serving static files and handling form uploads.
Handling Uploads: Could use Jetty’s servlet API or a microframework (like SparkJava or Javalin) for simplicity to handle file upload and then redirect to results.
Charts and UI: Use a JavaScript library such as Chart.js or D3.js for rendering charts (pie chart for state distribution, line chart for timeline, etc.), and perhaps a library like Datatables for the thread list table to allow searching/sorting. These can be included as static resources in the tool.
Tooltips: Could use a library like Bootstrap (which includes tooltip components) or simple custom tooltips.
UI Responsiveness and UX: The interface should be clean and not overly complicated. The focus is on clarity: using tables for data, charts for visualization, and clear text for explanations. Navigation can be through tabs or accordions for different sections. Since this runs locally, we assume a single user and no authentication needed. However, care should be taken not to expose this interface accidentally on public networks – by default it might bind to localhost only.
Performance: The Web UI must handle possibly large dumps gracefully. That means if there are hundreds of threads, the thread list table should be paginated or virtualized to not lag the browser. The server should send only necessary data (maybe compress JSON if large). But typically, this is manageable given threads count rarely exceed a few thousand in extreme cases.
Command-Line Interface (CLI)
The command-line interface allows using the analyzer in environments without a GUI or for quick analysis in scripts/automation. The CLI will be part of the same executable JAR and will invoke the same parsing and analysis logic as the Web UI. Invocation and Options: The CLI could be invoked by running something like:
java -jar ThreadDumpAnalyzer.jar [options] <thread_dump_files>
Where options might include:
Selecting output format: e.g. --out report.html to generate an HTML report, or --out report.pdf for PDF, or --format csv for a CSV output. If no output file is specified, the default could be a human-readable text output to the console.
Toggling certain analyses: e.g. --no-gui (which would be implicit if they're using CLI anyway), --compare (to indicate multiple dumps should be treated as a time series, though this could be automatic if multiple files are provided).
Verbosity or filtering options: e.g., --show-deadlocks-only or --state RUNNABLE to only display threads in a certain state (for console output cases).
Output in CLI: If printing to console (like stdout), the tool can present a text summary. For example:
Threads: 27 total (RUNNABLE: 24, WAITING: 2, TIMED_WAITING: 1, BLOCKED: 0)
Deadlocks: NONE
Most common stack trace locations:
  - 20 threads in java.lang.Object.wait() [likely idle threads]
  - 3 threads in com.example.Foo.processData(Foo.java:123)
...
Then possibly list top N threads or threads of interest:
ID     Name                  State      Note
0x12   http-nio-8080-exec-1  RUNNABLE   (on CPU, processing request)
0x13   http-nio-8080-exec-2  WAITING    (waiting on condition of task queue)
... 
The console output will be designed to fit typical terminal width, using indentation or truncation as needed. It should be readable and highlight anomalies (maybe mark deadlocked threads with *** or so). If the user requests an HTML output via CLI, the tool will essentially produce the same kind of HTML as the Web UI (perhaps a static file containing the charts and tables). This allows someone to generate a report and open it in a browser or send it to others, without running the web server. The design for this could reuse templates from the Web UI. For PDF export (via CLI or UI), the tool will convert either the HTML report to PDF or directly use a PDF library to lay out elements. Using an existing library is preferable – e.g., OpenPDF or iText to generate a PDF from HTML or from scratch. The PDF would contain the same information: maybe starting with an overview, then a section per thread with its stack trace (which could be lengthy). The PDF might include the charts as images (the tool can render charts to images using either an embedded browser rendering or a library like JFreeChart for simple charts in headless mode). Library Support (CLI): We will likely use a library for parsing command line arguments (such as Apache Commons CLI or Picocli) to define and parse options in a user-friendly way (providing help messages, etc.). This keeps the CLI interface robust and easy to extend with new options. Examples of Usage:
Single file, quick text output:
java -jar analyzer.jar dump1.txt
This would parse dump1.txt, run analysis, and print a summary to console.
Multiple files, HTML report:
java -jar analyzer.jar --out analysis.html dump1.txt dump2.txt dump3.txt
This processes the 3 dumps, performs the comparative analysis, and generates analysis.html. The user can open this file in a browser to view the report (which would contain interactive elements if self-contained with embedded scripts, or at least static charts/tables).
PDF output:
java -jar analyzer.jar --out analysis.pdf dump1.txt
Generates a PDF report from the single dump analysis.
The CLI ensures that all the capabilities of the tool (parsing all formats, detecting issues, comparing dumps, etc.) are accessible without the web interface. This is important for automation (for example, as part of a CI pipeline or when collecting dumps from a server and wanting an immediate analysis report).
Exporting and Reporting
Both the Web UI and CLI benefit from a common Reporting module that handles exporting analysis results to different formats. The design will incorporate the following:
HTML Report Generation: As discussed, we can either produce a dynamic HTML (for the live web UI) or a static HTML (for offline viewing). The static HTML could embed the necessary scripts and data so that it works standalone. For example, the report could embed a small script plus data in JSON form into the HTML so that charts render upon opening the file. Another simpler approach is to generate an HTML with SVG charts or pre-rendered images for charts (using server-side chart rendering). The report will include sections for each part of the analysis. Using a template engine here can help maintain a clean separation of content and layout.
PDF Export: Using a Java PDF library, the tool will convert either the HTML or directly assemble a PDF. This might involve rendering the charts as images. For instance, the implementation could take the chart data (like state distribution) and use a chart library (like JFreeChart) to draw a pie chart to a PNG image, then insert that image in the PDF along with textual content. Some libraries can directly convert HTML/CSS to PDF as well, which could be leveraged if our HTML report is well-structured. The PDF will be styled for readability (possibly portrait orientation with tables spanning pages if needed). It should include at least the summary and any critical findings, followed by either full thread listings or a subset (perhaps configurable or truncated if too long) because a full thread dump in PDF might be very lengthy. We might include an option to limit how much of each stack trace is included in the PDF report to keep it concise, with an option for full detail if needed.
CSV Export: The CSV is mainly useful for structured data like thread state counts or a list of threads. Potentially, multiple CSVs could be generated for different aspects:
One CSV for the thread summary (thread name, id, state, maybe waiting on which lock, etc.), where each thread is a row. This could be used in Excel or other tools for custom filtering.
Another CSV for state distribution over time if multiple dumps (columns: Dump name, RUNNABLE count, BLOCKED count, etc.).
Or a single CSV with multi-dump thread state changes (though that might be harder to represent in flat format).
However, to keep it simple, the initial idea is one CSV listing threads of the last (or only) dump with key details. If multiple dumps are loaded, perhaps the CSV could list threads from all dumps annotated by which dump they came from or separate CSV per dump.
Implementing these exports will reuse the analysis results. For example, the analysis engine might produce plain data objects or even textual segments that can be plugged into reports. Testing the exports is important (ensuring PDFs are not malformed, CSV opens correctly, etc.). Since the user expects a design, we note which libraries can help: Apache PDFBox or OpenPDF for PDF, standard Java file I/O for CSV (or a simple CSV library for escaping, like OpenCSV), and possibly using Jackson or similar if we need to output JSON for the web interface.
Putting it Together: Workflow Example
To illustrate how everything connects, consider a typical usage scenario: 1. User Interaction (Web UI): A user launches the JAR (via double-click or command line without arguments). The application starts an embedded Jetty server on localhost (say port 8080) and logs “Server started, open http://localhost:8080 in your browser.” The user opens that link, sees the upload page, and selects two thread dump files (for example, server_dump_1.txt and server_dump_2.txt). They click “Analyze”. The files are uploaded to the server. 2. Parsing and Analysis: The server receives the files (perhaps via a multipart HTTP request). It saves them to a temp location or streams them into the parser. It detects format for each (the first file might be a HotSpot dump, the second an OpenJ9 dump, for example). It invokes the respective parsers, building ThreadDump objects for each. Once parsed, it runs the analysis engine:
computes stats for each dump,
compares thread lists between dump1 and dump2,
finds no deadlocks but notices 15 threads appear in both dumps waiting on the same lock.
prepares data structures for charts (state counts, differences).
3. Rendering Results (Web): The server then either renders a results HTML page or returns JSON to the front-end. Suppose we chose server-side rendering for simplicity: it fills an HTML template with:
A table or list summarizing each dump (e.g., “Dump1 taken at 12:00: 50 threads; Dump2 at 12:30: 60 threads” and state breakdown).
An HTML canvas or SVG where a chart will be drawn (with embedded data or using a script that was included).
A table for thread list (maybe initially only showing aggregated view).
Sections for findings (like the lock contention message).
The browser displays this page. The user can click on a particular finding “15 threads waiting on lock X” which anchors them to the thread list filtered to those 15 threads, or highlights them. If the user hovers over a thread state in the table, a tooltip appears explaining that state (provided by a small JS snippet that references a dictionary of explanations or by the title attribute filled server-side). The user can navigate the results, expanding thread details as needed. 4. Optional Export: The user wants to save the analysis. The UI provides buttons like “Download PDF Report” or “Export CSV”. When clicked, these could trigger the server to run the corresponding export function. For example, clicking “Download PDF” could make the server generate the PDF on the fly (or have it pre-generated) and then respond with it as a file download. Alternatively, the client might have enough data to generate a CSV (like through a script constructing a CSV from the table) but server-side is simpler and more reliable for formats like PDF. In CLI usage, this step is analogous to specifying --out options. 5. Command-Line Usage Example: If the same user instead used the CLI: java -jar analyzer.jar --out analysis.html server_dump_1.txt server_dump_2.txt, the program would not start a server but would parse the files, run analysis, and then use the reporting module to produce analysis.html on disk. This HTML would be similar to what the web UI shows. They could open it in a browser and still get the charts (if we included the necessary scripts in it). The CLI might also print a brief summary to the console so the user knows analysis was done and file created, possibly highlighting if any severe issues (e.g., “Deadlock found!”) so that even without opening the report they get a heads-up. Throughout both interfaces, the same core logic is used for parsing and analysis, ensuring consistency. The separation of concerns in our architecture guarantees that improvements to analysis (say, a new check for a thread leak pattern) automatically reflect in both CLI and web outputs.
Implementation Considerations and Libraries
To implement this design in Java, we plan to leverage several libraries/frameworks to accelerate development and ensure reliability:
Embedded Web Server: Jetty is a strong choice for embedding. It can be started programmatically and used to serve servlets or JSP/HTML. Alternatively, a microframework like Javalin (which uses Jetty under the hood) could simplify the routing (for handling file uploads and serving results) with minimal boilerplate. Jetty provides the freedom to serve static content (like JS/CSS files for the UI) and dynamic handlers for analysis.
CLI Parsing: Apache Commons CLI or Picocli will be used to handle command-line options. This makes it easy to define flags like --out or --format and provide --help messages.
Parsing Thread Dumps: While custom parsing code will be written, if there are existing libraries for parsing thread dumps (such as the open-source ThreadLogic or others), we could consider incorporating them. However, given the need to support multiple formats and maintain control, a custom parser framework is likely. We will use standard Java IO (BufferedReader for text) and regex where appropriate (Java’s regex library for matching patterns like thread headers). This choice keeps the dependency count low.
Data Model: Plain Old Java Objects (POJOs) will suffice. No external library needed.
Charting and Visualization: For the Web UI, Chart.js (a JS library) can be included to draw pie and line charts using HTML5 canvas. It's lightweight and requires just providing data in JSON. If server-side rendering of charts is needed (for including in PDF), we might use JFreeChart to generate charts as images.
Templates/HTML: Thymeleaf or FreeMarker could be used to template the HTML pages if doing server-side rendering. Thymeleaf works well with embedded Spring, but we might not need the full Spring Boot stack. FreeMarker is a simple templating engine that can be used with Jetty easily for dynamic pages.
PDF Generation: OpenPDF (an open-source derivative of iText 4) can be used to programmatically create PDFs. Another approach is to use an HTML-to-PDF converter; for example, flying-saucer (XHTMLRenderer) which can take an XHTML/CSS and produce PDF – this works with well-formed HTML and a subset of CSS. If our HTML report is simple, we could use that. Otherwise, manually laying out a PDF via OpenPDF (writing paragraphs, tables, images) gives full control.
CSV: Use simple file writing. If using OpenCSV, it can simplify escaping, but it might be overkill for our needs. We’ll likely manually output CSV lines as the data is straightforward (commas separating fields, and quote-enclosing thread names since they can contain commas).
Testing: We should plan to test the tool with various thread dumps (from different JVMs, versions, including edge cases like extremely long stack traces, unusual characters in thread names, etc.). Also test the multi-dump comparison with dumps taken from the same JVM at different times to ensure the matching logic is correct.
Performance: In terms of performance, parsing and analysis of a single dump should be very fast (on the order of seconds or less). The most expensive operations might be rendering the UI or generating a PDF if there are thousands of threads (layout of so much text can be slow). We should ensure large text outputs are handled (maybe splitting PDF sections). For the web server, serving to a single user on localhost is not heavy, but we ensure to use efficient data structures and possibly limit extremely heavy operations (like not performing a quadratic comparison of every thread between dumps – instead using maps by thread ID for matching).
Extensibility: The design allows adding new features like support for additional dump formats (e.g., Android ART thread dumps if needed, or any new state in future Java versions). The analysis rules can grow – perhaps integrating with CPU profiling data or recognizing patterns for specific frameworks (like many threads stuck in a certain framework call might trigger a specific advice).
In summary, this Java Thread Dump Analyzer is designed to be a comprehensive yet easy-to-use tool, combining the convenience of a web interface with the practicality of a CLI. By structuring it with clear modules (Parsing, Data Model, Analysis, and Presentation) and leveraging known libraries (Jetty for web, etc.), the implementation will be manageable and maintainable. The end result will empower developers and support engineers to quickly interpret thread dumps – turning thousands of lines of stack traces into digestible insights – with charts, tables, and explanations that guide the troubleshooting process
blog.ycrash.io
. By facilitating loading multiple dumps and exporting reports, the tool will help in both ad-hoc analysis and formal reporting, all within a single self-contained Java application. Sources:
Oracle Java Troubleshooting Guide – thread states definitions
docs.oracle.com
docs.oracle.com
fastThread (yCrash) online analyzer – supports all JVM thread dump formats
fastthread.io
 and analyzes crash logs (hs_err)
fastthread.io
 and IBM javacore dumps
fastthread.io
.
yCrash Blog – recommends taking multiple thread dumps over time for better analysis
blog.ycrash.io
; demonstrates multi-dump comparative analysis
blog.ycrash.io
 and grouping of identical stack traces
blog.ycrash.io
.
IBM WAS Performance Cookbook – notes on IBM javacore thread states (e.g., Conditional Wait vs Runnable)
publib.boulder.ibm.com
.
Reflectoring.io – importance of thread dumps in diagnosing slowness, hangs, deadlocks
reflectoring.io
.
fastThread Blog (hs_err_pid analysis) – “All Threads” section provides thread details at crash
blog.fastthread.io
.

Favicon
Favicon
Favicon
Favicon
Favicon
Sources



No file chosenNo file chosen
ChatGPT can make mistakes. Check important info.

Java Thread Dump Analyzer with Web and CLI Interfaces
ChatGPT
I’m compiling a detailed design document for a Java-based thread dump analyzer, aiming for a zero-dependency application with both CLI and HTML web interface, effective parsing of varied formats, and advanced statistics and visualizations.

I'm consulting various Eclipse and IBM documents to gather details on OpenJ9 thread dump format differences. The design will guide whether extreme differences need highlighting.

Searched for open source Java thread dump analyzer
ChatGPT
I’m analyzing options like "Thread dump analyzer - open source : reddit" and "fastThread" for thread dump analysis. "ThreadLogic" on GitHub seems particularly promising.

Favicon
Read github.com

ChatGPT
ThreadLogic groups and classifies threads by patterns. It supports JVM thread dumps, including HotSpot and OpenJ9, and might include overlap with user needs.

Favicon
Read fastthread.io

ChatGPT
fastThread marketing highlights their tool’s ability to analyze thread dumps from various Java versions and provide features like dependency graphs and call trees. Analyzing their marketing claims and consulting structured guides.

Favicon
Read blog.ycrash.io

ChatGPT
OK, let me see. I’m piecing together if a screenshot could illustrate the fastThread UI, highlighting "multiple upload" and "summary view" for requirement #5.

Favicon
Read more from blog.ycrash.io

ChatGPT
I’m thinking through a Java Thread Dump Analyzer, covering multi-dump analysis, UI interactions, CLI design, and deadlock detection. Progressing to architecture, data models, and export methods.

Searched for OpenJ9 javacore thread dump example
ChatGPT
I’m thinking through IBM documentation, which probably includes an example thread dump.

Favicon
Read publib.boulder.ibm.com

ChatGPT
I'm refining how to differentiate thread dump formats for OpenJ9 and other JVMs by scanning file content and using specific parsers, like IBM's format and JSON dumps.

Searched for embedded Jetty CLI interface
ChatGPT
I'm considering whether to cite Jetty usage directly or provide a footnote to Jetty's site, as it might not be a typical "connected source we browsed."

Favicon
Read baeldung.com

ChatGPT
I’m piecing together why data isn’t fetching—could Cloudflare or a block be to blame?

Searched for analyze thread dumps Baeldung
ChatGPT
I'm working through potential usable tools like FastThread and JStack for analyzing thread dumps, focusing on their design aspects and comparative analysis features.

Searched for thread dump analysis thread state distribution
ChatGPT
Checking out Oracle docs to see if they have a thread state table. This seems useful for adding a states table.

Favicon
Read docs.oracle.com

ChatGPT
I'm piecing together a table to simplify thread states, possibly with short descriptions, listing major components and comparisons. Pondering embedding images for clarity.

Favicon
Read i0.wp.com

ChatGPT
I'm thinking through the use of images in the design doc. Third-party images are tricky but might clarify UI elements. Accessibility and clear citations are key.

Searched for thread dump analysis architecture diagram
ChatGPT
I’m checking the dev.to and reflectoring resources, but they likely lack visual aids. GCeasy, with its analysis patterns, potentially offers the necessary graphical metrics.

Favicon
Read reflectoring.io

ChatGPT
I’m gathering various references to ensure the answer is well-supported, including multi-dump comparisons, histograms, states, and specific commands like kill -3. This will provide a comprehensive view of the topic.

Searched for hs_err_pid thread dump content
Favicon
Read stackoverflow.com

ChatGPT
I'm piecing together a tool that effectively parses thread info from various multi-format and multi-file comparative analysis references. Analyzing thread states and multiple dumps is crucial, while kill -3 dumps might not be necessary.

Favicon
Read blog.ycrash.io

ChatGPT
I'm gathering evidence on multi-dump analysis, dynamic info, and context-sensitive UI. Considering deadlock detection and parsing thread dumps flexibly across formats.

Favicon
Read gceasy.io

ChatGPT
I'm figuring out if the GCeasy link has static content, as the snippet context suggests a GCeasy tool or ad page.

Favicon
Read more from gceasy.io

Searched for identify threads consuming CPU GCeasy
ChatGPT
I’m planning the architecture and features for the Java Thread Dump Analyzer, including parsing, analysis, and UI layers. Progress is steady.

Favicon
Read more from gceasy.io

ChatGPT
OK, let me see. I’m focusing on core needs: Multi-format and multi-file analysis, states clarity, and a comparative summary image. This leads me to consider critical progress.

Favicon
Read i0.wp.com

ChatGPT
OK, let me see. I’m thinking about embedding a pie chart from [17] to illustrate thread state distribution, offering a clear, concise visualization that aligns with the desired presentation.

I'm considering outlining the architecture overview and detailing module layers, considering parsing, analysis, and UI, while integrating class diagrams and differentiating OpenJ9 and HotSpot parsers.

Favicon
Read blog.fastthread.io

ChatGPT
I’ve identified that the hs_err blog outlines the "All Threads" section in Java Virtual Machine (JVM) crash logs, detailing thread states and counts, which aligns with our parsing needs.

Favicon
Read reflectoring.io

ChatGPT
I’m pulling together an introduction that emphasizes the significance of thread dumps for performance analysis and deadlock diagnosis, using specific references to highlight their role.

Favicon
Read fastthread.io

ChatGPT
I’m embedding citations to support the analysis of hs_err and IBM core dumps, with references covering format variations, support specifics, and deadlock detection.

Searched for OpenJ9 thread dump format differences
Favicon
Read more from i0.wp.com

Favicon
Read blog.fastthread.io

Favicon
Read reflectoring.io

ChatGPT
I’m consolidating the extracted data to finalize the content.

Research completed in 5m · 21 sources

Preview


Download PDF

Share Link
Design Document: Java Thread Dump Analyzer Tool
Introduction and Objectives
Java thread dumps are snapshots of all threads running in a JVM at a given instant. Each thread’s state is listed along with its stack trace, providing insight into what the application and JVM threads are doing
reflectoring.io
. A thread dump is a vital diagnostic tool for identifying performance bottlenecks (e.g. high CPU threads), unresponsive applications, and deadlock situations
reflectoring.io
. This design outlines a Java-based Thread Dump Analyzer that can be used by engineers to easily analyze such thread dumps and pinpoint issues. The primary objectives for this tool are:
Zero Dependencies: The analyzer should run as a standalone JAR with no external prerequisites (beyond a Java runtime). Users can simply run it without installing databases or app servers.
Dual Interface (CLI & Web): Provide both a command-line interface (for scripted or console use) and a user-friendly HTML Web UI (served via an embedded web server) for interactive analysis.
Multi-Format Support: Handle a wide range of thread dump formats – including standard HotSpot dumps (from jstack, jcmd or kill -3), hs_err_pid.log crash files, and IBM/OpenJ9 javacore dumps – and potentially even JSON-based dumps from monitoring tools. The tool must recognize and parse these formats automatically.
Analysis & Visualization: Generate meaningful statistics and visualizations from the thread data. This includes computing how many threads are in each state (RUNNABLE, BLOCKED, WAITING, etc.), identifying thread state distribution over time (when multiple dumps are provided), highlighting stack trace “hotspots” (e.g. common methods or locks where many threads converge), and detecting deadlocks or other synchronization issues. Results should be presented in tables, charts (like histograms or pie charts), and summaries for easy understanding.
Contextual Explanations: The Web UI should assist users in interpreting the data by providing context-sensitive help. For example, if a thread is in WAITING state or blocked on a lock, the UI can offer explanations or tooltips about what that means (pulled from known definitions or documentation)
docs.oracle.com
. Every part of the thread dump (thread states, stack frames, lock identifiers, etc.) should be accompanied by UI hints or documentation to educate users as they browse.
Multi-Dump Comparison: Allow users to load multiple thread dumps (e.g. a series of dumps taken over time) and compare them. This feature helps in analyzing how thread states evolve – for instance, to spot threads that are consistently stuck, to see growth in certain thread pools, or to catch deadlocks that occur after a certain point. The tool should present side-by-side comparisons or timeline views of thread state distributions and highlight differences between dumps (new threads, disappeared threads, changed states, etc.).
Report Exporting: Support exporting the analysis results into shareable report formats such as PDF (for a printable report), CSV (for raw data export like thread lists or statistics), and standalone HTML (a self-contained report that can be viewed in a browser without running the tool).
By meeting these objectives, the tool will enable comprehensive thread dump analysis with minimal setup, catering both to quick command-line use and in-depth visual exploration via a web browser.
System Architecture Overview
The Thread Dump Analyzer is designed with a modular architecture, separating concerns into distinct layers. The core components include a Parsing layer (to read various thread dump formats), an Analysis layer (to compute statistics and detect issues), a Data Model (to represent threads and dumps in memory), and a Presentation layer which encompasses both the CLI output and Web UI. The diagram below outlines the high-level architecture:
Input (Thread Dump files) → Parser Module (per format) → In-Memory Data Model → Analysis Engine → Outputs (CLI Report or Web UI).
Each part of the system is designed for extensibility — for example, adding support for a new dump format would mean adding a new parser implementation without altering the analysis logic. The table below summarizes the major modules and their responsibilities:
Module/Component	Responsibility
Thread Dump Parsers	Parse raw thread dump files of various formats (HotSpot, OpenJ9, etc.) into a structured internal model. Includes sub-parsers for each supported format.
Data Model	In-memory representation of thread dump data (threads, stack traces, locks, etc.), serving as a common input for analysis and output generation.
Analysis Engine	Processes Data Model to compute statistics (thread counts, state distribution, hotspots), detect deadlocks/livelocks, and prepare data for visualization.
Web UI (Embedded Server)	Presents an interactive HTML interface for analysis results. Handles file uploads, renders summary charts/tables, provides navigation through threads, and shows context help. Runs on an embedded HTTP server (e.g. Jetty) so users can access it via a browser with no separate deployment.
CLI Interface	Provides a command-line experience. Accepts input files and options, invokes parsers and analysis, and outputs results to console or files (supporting text, HTML, PDF, CSV outputs as specified by user).
Reporting/Export Module	Utilities to generate reports in various formats (HTML/PDF/CSV) from the analysis results, ensuring that both CLI and Web UI can export consistent reports.
All these components interact such that the Parsing layer feeds the Data Model, which is then examined by the Analysis layer. The results can be rendered either as textual output (CLI) or as rich visual output (Web UI), using the same underlying data and analysis.
Parsing Layer: Multi-Format Thread Dump Support
One of the key challenges is supporting the numerous thread dump formats produced by different JVMs and tools. The Parsing layer consists of a framework of parser classes that can ingest a dump file (often a plain text file) and produce a structured representation of the threads and their details. The design will include an abstract ThreadDumpParser interface or class, with concrete implementations such as HotSpotParser, OpenJ9Parser, etc. A factory or format detector will choose the appropriate parser by examining the file content (for example, looking for specific keywords or patterns unique to each format). Supported Formats: The tool will handle at least the following formats from the start:
HotSpot JVM thread dumps: These come from OpenJDK/Oracle JVMs via tools like jstack, jcmd Thread.print, or by sending SIGQUIT/Ctrl+Break (kill -3) to the process. They are textual dumps where each thread is listed with a header line (including the thread name, priority, tid, nid, state) followed by its stack frames and lock info. The parser will identify thread boundaries (often demarcated by a line starting with "Thread Name" or a line that includes tid=/nid=) and extract details.
HotSpot Error Logs (hs_err_pid.log):* These crash logs contain a wealth of information, including a section listing all threads and their states at the moment of crash. The parser will focus on the “All Threads” section of hs_err files
blog.fastthread.io
, extracting each thread’s stack trace and state. hs_err files also include native thread information and memory info, which might be parsed or skipped according to the tool’s focus. Support for hs_err files means users can analyze post-mortem crash data just like a live thread dump
fastthread.io
.
IBM/Eclipse OpenJ9 javacore dumps: OpenJ9 (formerly IBM J9) produces thread dumps in a structured text format often called javacore (e.g. files named javacore.DATE.TIME.pid.txt). These have a different syntax – threads are listed under sections with identifiers like 1XMTHDINFO, 3XMTHREADINFO etc., and thread states might be denoted by abbreviations (e.g., R for runnable, CW for conditional wait). The OpenJ9Parser will recognize these tokens and parse accordingly. It will convert IBM-specific state codes to the standard Java Thread.State where possible. For example, older IBM dumps might label certain threads as Conditional Wait (CW) which correspond to a thread that is actually waiting (or was runnable but waiting on a condition)
publib.boulder.ibm.com
. The parser will map those to a WAITING or BLOCKED state with additional info as needed. By supporting javacore dumps, the tool covers OpenJ9 and IBM JDK users
fastthread.io
.
Other formats: The architecture anticipates that some tools or APMs output thread dumps in JSON or other structured formats. As noted by prior art, thread dump format can vary by Java version, JVM vendor, and tooling (even JSON in some cases)
fastthread.io
. The parser layer can be extended with new implementations to handle these. For instance, a JsonThreadDumpParser could be added to handle dumps from a monitoring system that exports threads in JSON.
Parsing Strategy: Each parser will likely use pattern matching and state machines to read the file. For example, the HotSpot parser might use regex or string searches to find lines that mark the beginning of a new thread’s stack (identifiable by the quote " starting a thread name, or known keywords like daemon prio=). It will then capture subsequent lines until the next thread or end-of-dump. During parsing, relevant data to extract includes:
Thread name (e.g. "http-nio-8080-exec-10"),
Thread ID (sometimes a native ID or Java ID),
Thread state (e.g. RUNNABLE, WAITING on condition, TIMED_WAITING (on a monitor), BLOCKED on lock, etc.),
Stack trace elements (each stack frame with class, method, file, line, and maybe native line info),
Locked monitors and owned synchronizers (HotSpot lists these under each stack if -l option is used or in dumps by default),
The object or lock a thread is waiting on (if BLOCKED or WAITING on a monitor, the dump usually shows a line like “- waiting to lock <0x...> (a java.lang.Object)” which should be captured and linked to the thread holding that lock if possible).
For OpenJ9 dumps, the parser will read the formatted sections:
e.g. Thread Details: look for sections like 3XMTHREADINFO which contain thread ID, name, state (java/lang/Thread state: ...), native ID, etc.
Stack frames: denoted by identifiers like 4XESTACK lines for each frame.
Locking information: sections like 3XMLOCKINFO for contended locks, etc.
The parser will need to assemble this structured data into the common Data Model (described next).
To keep the tool extensible, adding a new parser does not affect other components – it simply needs to produce the data in the expected format. The parsing is synchronous and typically fast (thread dumps are text on the order of kilobytes to a few MB at most). Even so, the design will allow parsing in a background thread or concurrently (especially if the Web UI accepts multiple files at once) to keep the interface responsive.
In-Memory Data Model
Once a thread dump file is parsed, the information is stored in an in-memory Data Model that represents the threads, locks, and other relevant data. This model acts as a bridge between parsing and analysis, and it ensures the CLI and Web UI are using the same representation of the data. Core Classes and Structures: The data model can be object-oriented, roughly mapping to the following conceptual classes:
ThreadDump: Represents a single thread dump snapshot. Contains metadata like a timestamp (if known – e.g., the time the dump was taken or found in the file), an optional identifier (file name or user-given label), and a collection of threads. If the dump file provides JVM specifics (e.g. JVM version, uptime, etc.), those could be stored here as metadata as well.
ThreadInfo: Represents an individual thread within a dump. Key fields include:
threadId: a numeric identifier (for HotSpot this could be the native thread ID nid or the tid if available; for OpenJ9 the thread ID from javacore).
threadName: the name of the thread (as given in the dump).
threadState: the thread’s state (mapped to the standard java.lang.Thread.State enum if possible, e.g. NEW, RUNNABLE, BLOCKED, WAITING, TIMED_WAITING, TERMINATED). If the dump provides more nuanced states (like OpenJ9’s additional codes), those can be captured in additional attributes (e.g. an OpenJ9-specific state reason).
stackTrace: an ordered list of StackFrame objects representing the stack trace. Each StackFrame may include the class and method name, file and line number, and possibly a flag if it’s a native or JIT-compiled frame.
lockedMonitors: a list of LockInfo objects representing monitors the thread currently owns (for HotSpot, lines like “- locked <0x12345> (a SomeClass)”).
waitingOn: a LockInfo object if the thread is blocked or waiting on a monitor/lock (e.g. “waiting to lock <0x12345>”), indicating the lock it is trying to acquire.
lockedSynchronizers: (for HotSpot) any java.util.concurrent locks held (listed under “Locked ownable synchronizers” in the dump).
daemon, priority, etc.: Additional attributes as available (whether thread is daemon, OS priority, etc., as shown in some dumps).
LockInfo: Represents a lock or monitor identified in the dump (typically by an identity hash or address like 0x12345 and type). It may include:
id: an identifier (e.g., the memory address or unique hash code of the lock object).
className: the type of the lock object (e.g., java.lang.Object or a specific class if known).
ownerThread: (optional) reference to the ThreadInfo that currently holds this lock, if known. This can be populated during parsing or later analysis by cross-referencing threads that are holding versus waiting on locks.
All these are in-memory Java objects. For efficiency, cross-links can be set up (for example, each ThreadInfo waiting on a lock can have a reference to the ThreadInfo that owns that lock, if that thread is in the same dump). This makes analysis like deadlock detection easier. When multiple dumps are loaded (for comparison), the data model might hold them in a collection:
e.g., a ThreadDumpSeries object that contains multiple ThreadDump instances (with perhaps an overall label or ordering). However, it’s also feasible to just use a List<ThreadDump> in the analysis logic without a special container class. For clarity, the design can include a higher-level model like AnalysisSession that encapsulates either a single dump or a set of dumps being analyzed together.
Memory Considerations: Thread dumps are generally not huge (perhaps tens of thousands of lines at most if hundreds of threads), so representing them in memory is not too heavy. If analyzing many dumps at once, memory usage scales linearly. The design assumes this is acceptable for typical use (e.g., comparing 5-10 dumps). If needed, the tool could allow dumping older ThreadDump objects from memory once analysis is done, to conserve memory, but usually this isn’t necessary unless extremely large dumps or many dumps are used. The Data Model is crucial for decoupling input parsing from output generation. It provides a consistent API to query threads, which the Analysis Engine uses next.
Analysis Engine and Statistics Computation
Once the data model is populated for one or more thread dumps, the Analysis Engine runs a series of computations to derive insights. This module operates on the ThreadDump and ThreadInfo objects rather than the raw text, making it easier to compute metrics. Key functions of the Analysis Engine include:
Thread State Statistics: Tally the number of threads in each state for a single dump. For example, count how many are RUNNABLE, BLOCKED, WAITING, etc. This yields a distribution that can be presented as a summary table or chart (e.g., a pie chart of thread states). If multiple dumps are loaded, the engine can produce a state distribution for each dump and even a timeline of how these counts change over time.
Thread Categorization: Identify categories of threads (if possible) such as JVM internal threads vs application threads (perhaps by name patterns like those starting with “GC” or “Attach Listener” could be recognized as JVM threads). This can be used to filter or group threads in the UI.
Deadlock Detection: Analyze locks and waiting threads to detect deadlocks. HotSpot thread dumps explicitly list deadlocks at the end of the dump if any are found (“Found one Java-level deadlock…” along with the threads involved). The tool will capture that if present and mark those threads as deadlocked. Additionally, the engine can perform its own graph cycle detection: build a wait-for graph where nodes are threads and edges represent “Thread A waits on lock L held by Thread B”. By traversing this graph, any cycle indicates a deadlock. The engine would then identify all threads in the cycle and the locks involved. These can be reported clearly (e.g., “Threads T1 and T2 are deadlocked on locks L1 and L2”). This way, even in formats or cases where deadlocks are not pre-detected by the JVM (or if they involve more than two threads), the tool can still inform the user.
Lock Contention Analysis: Beyond deadlocks, the tool can identify heavily contended locks or hotspots. For example, if many threads are blocked on the same lock (many ThreadInfo objects have waitingOn the same LockInfo), that lock is a bottleneck. The analysis engine can flag this and report which lock and how many threads are waiting for it, and which thread holds it.
Stack Trace “Hotspots”: Determine if certain stack frames or methods are appearing frequently across many threads. For instance, if 50 threads are all showing java.util.Thread.sleep in their stack or are all in a particular method of the application, that method might be a hotspot or a point of contention. The engine could aggregate stack trace data to find the top N methods or classes that appear in the thread dump. This is analogous to finding common patterns – e.g., many threads waiting on java.sql.Connection.await might indicate a database connection pool exhaustion. These findings can be presented as a list of “common stack trace locations” or even as a flame graph visualization (collapsing identical stacks) in the UI. (In CLI mode, a textual summary of hottest stack frames could be shown.)
Thread grouping and naming patterns: The engine might group threads with identical stack traces
blog.ycrash.io
. This is useful to collapse duplicate information in the UI (e.g., show one stack trace and indicate “20 threads have this same stack trace” – often the case for thread pools where many threads are idle in the same place). It can also group by thread name patterns (for example, all “http-nio-8080-exec-*” threads can be recognized as a thread pool group). This grouping can make the analysis more digestible by not overwhelming the user with very similar threads.
CPU/Usage Indicators: Although a static thread dump does not directly show CPU usage, if the tool is given multiple dumps or additional info, it could infer some usage. For instance, a thread consistently appearing as RUNNABLE in every dump in the same stack location might be a busy loop consuming CPU. If integrated with an optional top -H snapshot (which lists thread IDs with CPU), the tool could even merge that info. However, at minimum, the engine can flag threads that are RUNNABLE in many dumps as likely CPU consumers.
Timeline / Evolution Analysis (for multiple dumps): When multiple dumps are loaded (assumed to be sequential snapshots of the same JVM over time), the engine compares them to find trends:
It can track a particular thread across dumps by thread ID or name. If a thread with the same ID appears in dump1 and dump2, it’s the same live thread. We can then see if its state changed, or if its stack trace progressed.
Threads that are new in later dumps (absent in earlier) can be identified (perhaps a new thread was created).
Threads that disappeared (present in earlier, gone in later) can indicate threads that finished execution.
By aligning threads, the engine can produce a comparative summary
blog.ycrash.io
. For example, a table of specific important threads showing their state in each dump side-by-side (highlighting if a thread went from RUNNABLE to BLOCKED, etc.). This is useful to spot patterns like a thread stuck in WAITING state throughout, or increasing numbers of threads in a certain pool.
Also, overall metrics over time: e.g., a chart of total thread count over dumps, or how RUNNABLE vs WAITING counts changed, could be produced.
The image below illustrates a comparative view of thread states across two dumps in a prior art tool – each row is a thread, and columns show its state in each dump (green = RUNNABLE, orange = WAITING, etc.), making it easy to spot changes or consistently stuck threads:
Example visualization of comparing two thread dumps, showing each thread’s state in Dump1 vs Dump2. This helps identify threads that remain stuck or change state over time (green = RUNNABLE, orange = WAITING, etc.), fulfilling the multi-dump analysis requirement.
Advisories and Patterns (Extensible): While not explicitly required, the design allows adding heuristic checks or “advisories.” For example, the analyzer might detect a thread pool starvation scenario or a particular deadlock pattern and provide a short description/advice. (This idea is inspired by existing tools that incorporate expert knowledge patterns
github.com
.) The architecture could include an advisory engine where rules can be added to flag known thread anti-patterns (e.g., too many threads in blocked state might indicate a concurrency bottleneck).
The Analysis Engine outputs data structures that represent these findings – e.g., a map of state -> count, lists of DeadlockInfo objects (if any deadlocks found, listing the threads and locks in cycle), a list of HotspotFrames (methods with high frequency counts), etc. These will then be used by the UI or CLI to present results to the user. By separating this logic, we ensure that the analysis can be invoked the same way from both interfaces and any heavy computation is done in one place.
User Interface – Web Application
The HTML/Web user interface is a crucial part of making the tool “user-friendly.” It will run on an embedded web server (such as Jetty or a similar lightweight Java HTTP server) launched by the application, so that when the user starts the tool (e.g., by running the JAR), it opens up a port (configurable, default say 8080) where the UI can be accessed via a browser. This approach means zero manual setup – no need to deploy a webapp to an external server; the server comes up automatically within the application process. Key aspects of the Web UI design:
Landing/Upload Page: When accessed initially, the web interface will present an upload form where users can select one or multiple thread dump files from their system. They can then start the analysis. The UI should allow multiple files to be uploaded at once (to support the comparison feature). For convenience, it may also list recently analyzed files or allow the user to paste raw thread dump text directly into a text area as an alternative input method.
Processing & Storage: Upon file upload, the server-side will invoke the parsing and analysis on those files. The results (the data models and analysis outputs) can be stored in memory (perhaps cached in the server session or a singleton since this is a single-user desktop tool typically). If multiple analysis sessions are needed, the tool could allow managing them, but initially focusing on one set of dumps at a time is fine. Once processed, the UI will navigate to the Results Dashboard.
Results Dashboard: This is the main view showing analysis of the loaded dump(s). It will include several sections:
Overview Summary: A high-level summary of the dump or dumps. For a single dump, this could be a line like “Total Threads: X; Runnable: Y, Blocked: Z, Waiting: W, Timed_Waiting: T, Terminated: U”. If multiple dumps, it could summarize each or highlight the differences (e.g., “Dump1 vs Dump2: total threads increased from 50 to 80, runnable threads from 10 to 5, etc.”).
State Distribution Chart: A visual chart (pie or bar chart) showing the proportion of threads in each state for the current dump. This helps quickly see if the majority are, say, waiting or runnable. For multiple dumps, the UI might show multiple pie charts side by side or a stacked bar chart per dump. Example: the UI could display a pie chart where slices represent RUNNABLE, BLOCKED, WAITING counts, so one can see at a glance how threads are divided. Below is an example of a summary view from an existing tool, illustrating how thread counts by state might be visualized:
Sample thread state summary visualization for a single dump (pie chart showing distribution of threads by state). Our tool will present similar histograms or charts to convey how many threads are RUNNABLE, BLOCKED, WAITING, etc., fulfilling the requirement for meaningful visual summaries.
blog.ycrash.io
Threads Table/List: A detailed list of threads, which can be initially grouped or filtered. For instance, threads could be grouped by state or by name pattern. The user can expand a group to see individual threads. Each thread entry in the list might show: Name, ID, State, and possibly the first stack frame or an important marker (like “waiting on X lock” or “at Y method…”). This list should be sortable and searchable (e.g., filter by thread name).
Stack Trace Viewer: When a user clicks on a specific thread from the list, the UI should display the full stack trace of that thread. This can be on the same page (e.g., expanding below the thread in the list) or a separate pane. The stack trace view should highlight lines that are of interest: for example, the line where it’s blocked or waiting (and indicate which lock it’s waiting on, possibly linking to the thread that holds that lock). If the thread is in a deadlock, that could be indicated in red, etc.
Analysis Highlights: Apart from raw thread data, the UI can have a section for “Notable Findings” which surfaces key points from the analysis engine. For example:
“Deadlock detected involving 2 threads (Thread-A and Thread-B).” This can be shown prominently, with an option to jump to those threads or to a deadlock details view.
“5 threads are waiting on the same lock (com.example.Foo$Bar lock) held by Thread-X – possible bottleneck.”
“All 20 worker threads have identical stack traces (they are all in Object.wait() on a task queue)
blog.ycrash.io
.” This tells the user that those threads might be idle waiting for work.
“Thread Y appears in RUNNABLE state across all 3 dumps, at the same code location – possible hot loop.”
These insights make the tool more than just a data viewer, but an analytical assistant.
Comparison View (if multiple dumps): When multiple dumps are loaded, the UI should provide a way to compare them. This could be a dedicated tab or section. For example:
A timeline chart could illustrate the count of each thread state across dumps (x-axis: time or dump sequence, y-axis: number of threads in a given state, with multiple lines or areas for each state). This shows trends (e.g., gradually rising blocked threads).
A comparative threads table: as mentioned, list key threads and how their state changed dump-to-dump. Possibly the UI allows selecting two specific dumps to diff. Or a play-through where you can click “next dump” and see the changes highlighted.
The UI can also simply list threads that are new or gone between dumps.
Given the complexity, an initial approach is to highlight differences in textual form (e.g., “New threads in Dump2: X, Y; Threads terminated by Dump2: Z; Threads that changed state: A from WAITING to RUNNABLE”). This textual summary, along with charts, addresses the evolving state analysis requirement.
Context-Sensitive Help: To make the UI educational, certain terms and sections will have explanatory pop-ups or links. For example:
Hovering over a thread State (like “TIMED_WAITING”) could show a tooltip: “TIMED_WAITING: The thread is waiting for an action for up to a specified time
docs.oracle.com
.” Similarly, hovering over “BLOCKED” might show “BLOCKED: The thread is blocked waiting for a monitor lock
docs.oracle.com
.” These descriptions can be sourced from official Java documentation.
If a thread is shown as daemon, that term could have a tooltip explaining what a daemon thread is.
The UI might include an integrated help panel or info icons next to sections like the charts or deadlock reports, explaining how to interpret them. For instance, an info icon next to the state distribution chart can explain the thread states briefly, or next to a deadlock warning can explain what a deadlock means.
A small “Legend” section can clarify color codes used in charts or comparison views (e.g., what each color or symbol stands for).
Technology Stack (UI): The Web UI will be generated by the Java application. There are a couple of approaches:
Server-Side Rendered: The application could use a Java templating engine (like FreeMarker, Thymeleaf, or JSP with Jetty) to produce HTML pages populated with the analysis data. This would result in mostly static pages (with maybe some inline JavaScript for minor interactions).
Single Page Application: Alternatively, the embedded server could serve a static front-end (HTML/JavaScript) and expose REST endpoints that provide the analysis data in JSON. For example, after upload, the browser could load a page that uses AJAX to fetch “/api/summary” or “/api/threads” etc. This would allow a more dynamic experience (e.g., filtering/sorting in the browser). A lightweight JS framework or even vanilla JS with a library like Chart.js for charts could be used.
Given the requirement of simplicity, a hybrid approach might be simplest: generate an HTML report with embedded charts (possibly using a JS chart library but pre-feeding it data). However, for interactivity like expanding threads and tooltips, some JavaScript is needed. The design leans towards using popular libraries:
Embedded Server: Jetty (as suggested) or an alternative like Spring Boot with an embedded Tomcat. Jetty is lightweight and can be programmatically started on a port, serving static files and handling form uploads.
Handling Uploads: Could use Jetty’s servlet API or a microframework (like SparkJava or Javalin) for simplicity to handle file upload and then redirect to results.
Charts and UI: Use a JavaScript library such as Chart.js or D3.js for rendering charts (pie chart for state distribution, line chart for timeline, etc.), and perhaps a library like Datatables for the thread list table to allow searching/sorting. These can be included as static resources in the tool.
Tooltips: Could use a library like Bootstrap (which includes tooltip components) or simple custom tooltips.
UI Responsiveness and UX: The interface should be clean and not overly complicated. The focus is on clarity: using tables for data, charts for visualization, and clear text for explanations. Navigation can be through tabs or accordions for different sections. Since this runs locally, we assume a single user and no authentication needed. However, care should be taken not to expose this interface accidentally on public networks – by default it might bind to localhost only.
Performance: The Web UI must handle possibly large dumps gracefully. That means if there are hundreds of threads, the thread list table should be paginated or virtualized to not lag the browser. The server should send only necessary data (maybe compress JSON if large). But typically, this is manageable given threads count rarely exceed a few thousand in extreme cases.
Command-Line Interface (CLI)
The command-line interface allows using the analyzer in environments without a GUI or for quick analysis in scripts/automation. The CLI will be part of the same executable JAR and will invoke the same parsing and analysis logic as the Web UI. Invocation and Options: The CLI could be invoked by running something like:
java -jar ThreadDumpAnalyzer.jar [options] <thread_dump_files>
Where options might include:
Selecting output format: e.g. --out report.html to generate an HTML report, or --out report.pdf for PDF, or --format csv for a CSV output. If no output file is specified, the default could be a human-readable text output to the console.
Toggling certain analyses: e.g. --no-gui (which would be implicit if they're using CLI anyway), --compare (to indicate multiple dumps should be treated as a time series, though this could be automatic if multiple files are provided).
Verbosity or filtering options: e.g., --show-deadlocks-only or --state RUNNABLE to only display threads in a certain state (for console output cases).
Output in CLI: If printing to console (like stdout), the tool can present a text summary. For example:
Threads: 27 total (RUNNABLE: 24, WAITING: 2, TIMED_WAITING: 1, BLOCKED: 0)
Deadlocks: NONE
Most common stack trace locations:
  - 20 threads in java.lang.Object.wait() [likely idle threads]
  - 3 threads in com.example.Foo.processData(Foo.java:123)
...
Then possibly list top N threads or threads of interest:
ID     Name                  State      Note
0x12   http-nio-8080-exec-1  RUNNABLE   (on CPU, processing request)
0x13   http-nio-8080-exec-2  WAITING    (waiting on condition of task queue)
... 
The console output will be designed to fit typical terminal width, using indentation or truncation as needed. It should be readable and highlight anomalies (maybe mark deadlocked threads with *** or so). If the user requests an HTML output via CLI, the tool will essentially produce the same kind of HTML as the Web UI (perhaps a static file containing the charts and tables). This allows someone to generate a report and open it in a browser or send it to others, without running the web server. The design for this could reuse templates from the Web UI. For PDF export (via CLI or UI), the tool will convert either the HTML report to PDF or directly use a PDF library to lay out elements. Using an existing library is preferable – e.g., OpenPDF or iText to generate a PDF from HTML or from scratch. The PDF would contain the same information: maybe starting with an overview, then a section per thread with its stack trace (which could be lengthy). The PDF might include the charts as images (the tool can render charts to images using either an embedded browser rendering or a library like JFreeChart for simple charts in headless mode). Library Support (CLI): We will likely use a library for parsing command line arguments (such as Apache Commons CLI or Picocli) to define and parse options in a user-friendly way (providing help messages, etc.). This keeps the CLI interface robust and easy to extend with new options. Examples of Usage:
Single file, quick text output:
java -jar analyzer.jar dump1.txt
This would parse dump1.txt, run analysis, and print a summary to console.
Multiple files, HTML report:
java -jar analyzer.jar --out analysis.html dump1.txt dump2.txt dump3.txt
This processes the 3 dumps, performs the comparative analysis, and generates analysis.html. The user can open this file in a browser to view the report (which would contain interactive elements if self-contained with embedded scripts, or at least static charts/tables).
PDF output:
java -jar analyzer.jar --out analysis.pdf dump1.txt
Generates a PDF report from the single dump analysis.
The CLI ensures that all the capabilities of the tool (parsing all formats, detecting issues, comparing dumps, etc.) are accessible without the web interface. This is important for automation (for example, as part of a CI pipeline or when collecting dumps from a server and wanting an immediate analysis report).
Exporting and Reporting
Both the Web UI and CLI benefit from a common Reporting module that handles exporting analysis results to different formats. The design will incorporate the following:
HTML Report Generation: As discussed, we can either produce a dynamic HTML (for the live web UI) or a static HTML (for offline viewing). The static HTML could embed the necessary scripts and data so that it works standalone. For example, the report could embed a small script plus data in JSON form into the HTML so that charts render upon opening the file. Another simpler approach is to generate an HTML with SVG charts or pre-rendered images for charts (using server-side chart rendering). The report will include sections for each part of the analysis. Using a template engine here can help maintain a clean separation of content and layout.
PDF Export: Using a Java PDF library, the tool will convert either the HTML or directly assemble a PDF. This might involve rendering the charts as images. For instance, the implementation could take the chart data (like state distribution) and use a chart library (like JFreeChart) to draw a pie chart to a PNG image, then insert that image in the PDF along with textual content. Some libraries can directly convert HTML/CSS to PDF as well, which could be leveraged if our HTML report is well-structured. The PDF will be styled for readability (possibly portrait orientation with tables spanning pages if needed). It should include at least the summary and any critical findings, followed by either full thread listings or a subset (perhaps configurable or truncated if too long) because a full thread dump in PDF might be very lengthy. We might include an option to limit how much of each stack trace is included in the PDF report to keep it concise, with an option for full detail if needed.
CSV Export: The CSV is mainly useful for structured data like thread state counts or a list of threads. Potentially, multiple CSVs could be generated for different aspects:
One CSV for the thread summary (thread name, id, state, maybe waiting on which lock, etc.), where each thread is a row. This could be used in Excel or other tools for custom filtering.
Another CSV for state distribution over time if multiple dumps (columns: Dump name, RUNNABLE count, BLOCKED count, etc.).
Or a single CSV with multi-dump thread state changes (though that might be harder to represent in flat format).
However, to keep it simple, the initial idea is one CSV listing threads of the last (or only) dump with key details. If multiple dumps are loaded, perhaps the CSV could list threads from all dumps annotated by which dump they came from or separate CSV per dump.
Implementing these exports will reuse the analysis results. For example, the analysis engine might produce plain data objects or even textual segments that can be plugged into reports. Testing the exports is important (ensuring PDFs are not malformed, CSV opens correctly, etc.). Since the user expects a design, we note which libraries can help: Apache PDFBox or OpenPDF for PDF, standard Java file I/O for CSV (or a simple CSV library for escaping, like OpenCSV), and possibly using Jackson or similar if we need to output JSON for the web interface.
Putting it Together: Workflow Example
To illustrate how everything connects, consider a typical usage scenario: 1. User Interaction (Web UI): A user launches the JAR (via double-click or command line without arguments). The application starts an embedded Jetty server on localhost (say port 8080) and logs “Server started, open http://localhost:8080 in your browser.” The user opens that link, sees the upload page, and selects two thread dump files (for example, server_dump_1.txt and server_dump_2.txt). They click “Analyze”. The files are uploaded to the server. 2. Parsing and Analysis: The server receives the files (perhaps via a multipart HTTP request). It saves them to a temp location or streams them into the parser. It detects format for each (the first file might be a HotSpot dump, the second an OpenJ9 dump, for example). It invokes the respective parsers, building ThreadDump objects for each. Once parsed, it runs the analysis engine:
computes stats for each dump,
compares thread lists between dump1 and dump2,
finds no deadlocks but notices 15 threads appear in both dumps waiting on the same lock.
prepares data structures for charts (state counts, differences).
3. Rendering Results (Web): The server then either renders a results HTML page or returns JSON to the front-end. Suppose we chose server-side rendering for simplicity: it fills an HTML template with:
A table or list summarizing each dump (e.g., “Dump1 taken at 12:00: 50 threads; Dump2 at 12:30: 60 threads” and state breakdown).
An HTML canvas or SVG where a chart will be drawn (with embedded data or using a script that was included).
A table for thread list (maybe initially only showing aggregated view).
Sections for findings (like the lock contention message).
The browser displays this page. The user can click on a particular finding “15 threads waiting on lock X” which anchors them to the thread list filtered to those 15 threads, or highlights them. If the user hovers over a thread state in the table, a tooltip appears explaining that state (provided by a small JS snippet that references a dictionary of explanations or by the title attribute filled server-side). The user can navigate the results, expanding thread details as needed. 4. Optional Export: The user wants to save the analysis. The UI provides buttons like “Download PDF Report” or “Export CSV”. When clicked, these could trigger the server to run the corresponding export function. For example, clicking “Download PDF” could make the server generate the PDF on the fly (or have it pre-generated) and then respond with it as a file download. Alternatively, the client might have enough data to generate a CSV (like through a script constructing a CSV from the table) but server-side is simpler and more reliable for formats like PDF. In CLI usage, this step is analogous to specifying --out options. 5. Command-Line Usage Example: If the same user instead used the CLI: java -jar analyzer.jar --out analysis.html server_dump_1.txt server_dump_2.txt, the program would not start a server but would parse the files, run analysis, and then use the reporting module to produce analysis.html on disk. This HTML would be similar to what the web UI shows. They could open it in a browser and still get the charts (if we included the necessary scripts in it). The CLI might also print a brief summary to the console so the user knows analysis was done and file created, possibly highlighting if any severe issues (e.g., “Deadlock found!”) so that even without opening the report they get a heads-up. Throughout both interfaces, the same core logic is used for parsing and analysis, ensuring consistency. The separation of concerns in our architecture guarantees that improvements to analysis (say, a new check for a thread leak pattern) automatically reflect in both CLI and web outputs.
Implementation Considerations and Libraries
To implement this design in Java, we plan to leverage several libraries/frameworks to accelerate development and ensure reliability:
Embedded Web Server: Jetty is a strong choice for embedding. It can be started programmatically and used to serve servlets or JSP/HTML. Alternatively, a microframework like Javalin (which uses Jetty under the hood) could simplify the routing (for handling file uploads and serving results) with minimal boilerplate. Jetty provides the freedom to serve static content (like JS/CSS files for the UI) and dynamic handlers for analysis.
CLI Parsing: Apache Commons CLI or Picocli will be used to handle command-line options. This makes it easy to define flags like --out or --format and provide --help messages.
Parsing Thread Dumps: While custom parsing code will be written, if there are existing libraries for parsing thread dumps (such as the open-source ThreadLogic or others), we could consider incorporating them. However, given the need to support multiple formats and maintain control, a custom parser framework is likely. We will use standard Java IO (BufferedReader for text) and regex where appropriate (Java’s regex library for matching patterns like thread headers). This choice keeps the dependency count low.
Data Model: Plain Old Java Objects (POJOs) will suffice. No external library needed.
Charting and Visualization: For the Web UI, Chart.js (a JS library) can be included to draw pie and line charts using HTML5 canvas. It's lightweight and requires just providing data in JSON. If server-side rendering of charts is needed (for including in PDF), we might use JFreeChart to generate charts as images.
Templates/HTML: Thymeleaf or FreeMarker could be used to template the HTML pages if doing server-side rendering. Thymeleaf works well with embedded Spring, but we might not need the full Spring Boot stack. FreeMarker is a simple templating engine that can be used with Jetty easily for dynamic pages.
PDF Generation: OpenPDF (an open-source derivative of iText 4) can be used to programmatically create PDFs. Another approach is to use an HTML-to-PDF converter; for example, flying-saucer (XHTMLRenderer) which can take an XHTML/CSS and produce PDF – this works with well-formed HTML and a subset of CSS. If our HTML report is simple, we could use that. Otherwise, manually laying out a PDF via OpenPDF (writing paragraphs, tables, images) gives full control.
CSV: Use simple file writing. If using OpenCSV, it can simplify escaping, but it might be overkill for our needs. We’ll likely manually output CSV lines as the data is straightforward (commas separating fields, and quote-enclosing thread names since they can contain commas).
Testing: We should plan to test the tool with various thread dumps (from different JVMs, versions, including edge cases like extremely long stack traces, unusual characters in thread names, etc.). Also test the multi-dump comparison with dumps taken from the same JVM at different times to ensure the matching logic is correct.
Performance: In terms of performance, parsing and analysis of a single dump should be very fast (on the order of seconds or less). The most expensive operations might be rendering the UI or generating a PDF if there are thousands of threads (layout of so much text can be slow). We should ensure large text outputs are handled (maybe splitting PDF sections). For the web server, serving to a single user on localhost is not heavy, but we ensure to use efficient data structures and possibly limit extremely heavy operations (like not performing a quadratic comparison of every thread between dumps – instead using maps by thread ID for matching).
Extensibility: The design allows adding new features like support for additional dump formats (e.g., Android ART thread dumps if needed, or any new state in future Java versions). The analysis rules can grow – perhaps integrating with CPU profiling data or recognizing patterns for specific frameworks (like many threads stuck in a certain framework call might trigger a specific advice).
In summary, this Java Thread Dump Analyzer is designed to be a comprehensive yet easy-to-use tool, combining the convenience of a web interface with the practicality of a CLI. By structuring it with clear modules (Parsing, Data Model, Analysis, and Presentation) and leveraging known libraries (Jetty for web, etc.), the implementation will be manageable and maintainable. The end result will empower developers and support engineers to quickly interpret thread dumps – turning thousands of lines of stack traces into digestible insights – with charts, tables, and explanations that guide the troubleshooting process
blog.ycrash.io
. By facilitating loading multiple dumps and exporting reports, the tool will help in both ad-hoc analysis and formal reporting, all within a single self-contained Java application. Sources:
Oracle Java Troubleshooting Guide – thread states definitions
docs.oracle.com
docs.oracle.com
fastThread (yCrash) online analyzer – supports all JVM thread dump formats
fastthread.io
 and analyzes crash logs (hs_err)
fastthread.io
 and IBM javacore dumps
fastthread.io
.
yCrash Blog – recommends taking multiple thread dumps over time for better analysis
blog.ycrash.io
; demonstrates multi-dump comparative analysis
blog.ycrash.io
 and grouping of identical stack traces
blog.ycrash.io
.
IBM WAS Performance Cookbook – notes on IBM javacore thread states (e.g., Conditional Wait vs Runnable)
publib.boulder.ibm.com
.
Reflectoring.io – importance of thread dumps in diagnosing slowness, hangs, deadlocks
reflectoring.io
.
fastThread Blog (hs_err_pid analysis) – “All Threads” section provides thread details at crash
blog.fastthread.io
.
Citations
Favicon
Creating and Analyzing Thread Dumps

https://reflectoring.io/analyzing-thread-dumps/
Favicon
Creating and Analyzing Thread Dumps

https://reflectoring.io/analyzing-thread-dumps/
Favicon
Thread States for a Thread Dump

https://docs.oracle.com/javase/8/docs/technotes/guides/troubleshoot/tooldescr034.html
Favicon
Exploring the Java ‘hs_err_pid’ file - Fast thread

https://blog.fastthread.io/exploring-the-java-hs_err_pid-file/
Smart Java thread dump analyzer - thread dump analysis in seconds

https://fastthread.io/
Favicon
IBM WebSphere Application Server Performance Cookbook - Troubleshooting OpenJ9 and IBM J9 JVMs

https://publib.boulder.ibm.com/httpserv/cookbook/Troubleshooting-Troubleshooting_Java-Troubleshooting_OpenJ9_and_IBM_J9_JVMs.html
Smart Java thread dump analyzer - thread dump analysis in seconds

https://fastthread.io/
Smart Java thread dump analyzer - thread dump analysis in seconds

https://fastthread.io/
Favicon
Simplifying Thread Dump Analysis: A Comprehensive Guide

https://blog.ycrash.io/simplifying-thread-dump-analysis-guide/
Favicon
Simplifying Thread Dump Analysis: A Comprehensive Guide

https://blog.ycrash.io/simplifying-thread-dump-analysis-guide/
Favicon
GitHub - sparameswaran/threadlogic

https://github.com/sparameswaran/threadlogic
Favicon
Simplifying Thread Dump Analysis: A Comprehensive Guide

https://blog.ycrash.io/simplifying-thread-dump-analysis-guide/
Favicon
Thread States for a Thread Dump

https://docs.oracle.com/javase/8/docs/technotes/guides/troubleshoot/tooldescr034.html
Favicon
Simplifying Thread Dump Analysis: A Comprehensive Guide

https://blog.ycrash.io/simplifying-thread-dump-analysis-guide/
Favicon
Simplifying Thread Dump Analysis: A Comprehensive Guide

https://blog.ycrash.io/simplifying-thread-dump-analysis-guide/
All Sources
Favicon
reflectoring
Favicon
docs.oracle
Favicon
blog.fastthread
fastthread
Favicon
publib.boulder.ibm
Favicon
blog.ycrash
Favicon
github

## Usage

### Building

Use Maven to build the CLI module and create a standalone JAR:

```sh
mvn -q package -DskipTests -pl cli -am
```

The resulting JAR will be located at `cli/target/cli-0.1.0-SNAPSHOT-shaded.jar`.

### Command Line

Run the analyzer directly with Java and one or more thread dump files:

```sh
java -jar analyzer.jar dump1.txt dump2.txt
```

Use the `--out` option to write an HTML, PDF, or CSV report:

```sh
java -jar analyzer.jar --out analysis.html dump1.txt
```

### Web Interface

Simply execute the JAR with no arguments:

```sh
java -jar analyzer.jar
```

Then open `http://localhost:8080` in your browser. Upload one or more thread
dump files using the form and click **Analyze** to view charts and tables. The
web interface also offers download links for PDF or CSV exports.
