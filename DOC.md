# Java Thread Dump Analyzer User Guide

This guide describes how to build and run the thread dump analyzer.

## Building

The project uses Maven. To compile all modules and create the CLI application run:

```bash
mvn -q package
```

The shaded CLI JAR will be created in `cli/target/`.

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

## Running the Web Server (Experimental)

A minimal Jetty server is provided. Start it with:

```bash
java -cp web/target/web-0.1.0-SNAPSHOT.jar com.example.web.WebServer [PORT]
```
If `PORT` is omitted, the server uses `8080`. You may also set the environment
variable `ANALYZER_PORT` to specify the port. The server binds only to
`localhost` by default to avoid remote exposure.

Then open `http://localhost:<PORT>` in your browser. The web interface is under
development and currently only starts the server.

## Keeping This Guide Updated

Whenever the application gains new functionality, please update `DOC.md` so that new users can follow along.
