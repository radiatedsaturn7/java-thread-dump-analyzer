# Java Thread Dump Analyzer User Guide

This guide describes how to build and run the thread dump analyzer.

## Building

The project uses Maven. To compile all modules and create the CLI application run:

```bash
mvn -q package
```

The shaded CLI JAR will be created in `cli/target/`.

## Git LFS

Large sample thread dumps for tests are stored with Git LFS. After cloning this
repository, run `git lfs install` once and then `git lfs pull` to fetch these
files. If you add new large dumps, track them with `git lfs track`.

## Using the Command Line Interface

Run the CLI with one or more thread dump files:

```bash
java -jar cli/target/cli-0.1.0-SNAPSHOT.jar dump1.txt dump2.txt
```

Gzip-compressed files (`.gz`) are detected automatically.
The analyzer prints a count of threads by state for each file.
To list only threads in a specific state, use:

```bash
java -jar cli/target/cli-0.1.0-SNAPSHOT.jar --filter-state RUNNABLE dump.txt
```

To display the most common stack frames, specify a limit with `--hotspots`:

```bash
java -jar cli/target/cli-0.1.0-SNAPSHOT.jar --hotspots 5 dump.txt
```

To display only detected deadlocks, pass `--show-deadlocks-only`:

```bash
java -jar cli/target/cli-0.1.0-SNAPSHOT.jar --show-deadlocks-only dump.txt
```

To compare two dumps and highlight new and disappeared threads, use `--diff` with two files:

```bash
java -jar cli/target/cli-0.1.0-SNAPSHOT.jar --diff before.txt after.txt
```

To display thread state counts for several dumps in order, use `--timeline` with multiple files:

```bash
java -jar cli/target/cli-0.1.0-SNAPSHOT.jar --timeline dump1.txt dump2.txt
```

To find threads that remain RUNNABLE across multiple dumps, pass `--highcpu` with the dumps:

```bash
java -jar cli/target/cli-0.1.0-SNAPSHOT.jar --highcpu dump1.txt dump2.txt
```

To check for potential thread pool starvation across one or more dumps, use `--starvation`:

```bash
java -jar cli/target/cli-0.1.0-SNAPSHOT.jar --starvation dump1.txt dump2.txt
```

This prints the counts per dump or outputs JSON when combined with `--format json`.

The CLI can output results in JSON format instead of plain text with `--format json`:

```bash
java -jar cli/target/cli-0.1.0-SNAPSHOT.jar --format json dump.txt
```
When using the custom JSON dump format, you can include optional fields such as
`jvmVersion` and `jvmUptime` at the root, and `priority` and `daemon` for each
thread entry. These values are parsed if present.
You can also use the convenience option `--output-json` which is equivalent to
specifying `--format json`:
```bash
java -jar cli/target/cli-0.1.0-SNAPSHOT.jar --output-json dump.txt
```

You can also control which analysis features are displayed by listing them with `--features`.
Available options are `counts`, `deadlocks`, and `hotspots` (the default is all):

```bash
java -jar cli/target/cli-0.1.0-SNAPSHOT.jar --features counts,hotspots dump.txt
```

If you generate an HTML report with `-o report.html`, you can automatically open
it in your browser by adding `--open`:

```bash
java -jar cli/target/cli-0.1.0-SNAPSHOT.jar -o report.html --open dump.txt
```

You can give a thread dump a custom label for display using `--label`:

```bash
java -jar cli/target/cli-0.1.0-SNAPSHOT.jar --label MyDump dump.txt
```

Clear any cached dumps before running with `--clear-cache`:

```bash
java -jar cli/target/cli-0.1.0-SNAPSHOT.jar --clear-cache dump.txt
```

List the supported thread dump formats with `--list-parsers`:

```bash
java -jar cli/target/cli-0.1.0-SNAPSHOT.jar --list-parsers
```

Show the application version with `--version`:

```bash
java -jar cli/target/cli-0.1.0-SNAPSHOT.jar --version
```

## Running the Web Server (Experimental)

A minimal Jetty server is provided. Start it with:

```bash
java -cp web/target/web-0.1.0-SNAPSHOT.jar com.example.web.WebServer [PORT]
```
If `PORT` is omitted, the server uses `8080`. You may also set the environment
variable `ANALYZER_PORT` to specify the port. The server binds only to
`localhost` by default to avoid remote exposure.

Then open `http://localhost:<PORT>` in your browser. The page shows a simple
form allowing you to upload one or more thread dump files at once. If you prefer,
paste a dump directly into the text area on the page. After submitting files or
pasted text, the server parses each dump and prints a table of thread state counts for every file.
Parsed dumps are cached in memory so uploading the same file again will reuse
the cached result and return counts more quickly. The cache holds up to ten
distinct dumps; when it grows beyond this size the least recently used entry
is evicted to free memory.
You can clear all cached dumps at any time using the **Clear Cache** button on
the upload page.
Recent file names are listed on the upload page so you can see which
dumps were analyzed most recently.
If you upload exactly two dumps at once the server will also display which
threads are new in the second dump and which disappeared since the first.
If multiple dumps are uploaded, the server also lists any threads that remain
`RUNNABLE` in all of them as potential **high CPU** candidates.

## Keeping This Guide Updated

Whenever the application gains new functionality, please update `DOC.md` so that new users can follow along.
