[![Build Status](https://travis-ci.org/8eo/boris.svg?branch=development)](https://travis-ci.org/8eo/boris)
[<img src="https://img.shields.io/badge/horn-%3EBoris%E2%80%85the%E2%80%85Blade-green.svg">](https://horn.co/%3EBoris%E2%80%85the%E2%80%85Blade)

# Boris #
[Boris the Blade](https://en.wikipedia.org/wiki/Snatch_(film)) can not be killed! The goal is to 
provide a robust, reactive and failsafe ReST client for accessing single or  multiple (redundant) 
servers providing a service. In the case of multiple identical servers, Boris will sent successive 
requests to the next server in it's list (Round Robin). Failed requests are simply repeated on the 
next server. It will only fail if all server options have been exhausted after several tries. 
Individual servers that return repeated failures are removed from the pool for a set 
period of time. Boris is a thin layer over the awesome 
[Akka HTTP](http://doc.akka.io/docs/akka-http/current/scala.html) project so all response values 
are Akka streams.

Clone this project and install Boris using
```bash
sbt publishLocal
```
For Scala 2.12, use 
```bash
sbt ++2.12.0 publishLocal
```

You can include it in your project by adding the following dependency:
```scala
libraryDependencies += "co.horn" %% "boris" % "0.0.5"
```

If there is sufficient interest from the community we will publish it on Maven Central.

# Usage
Using Boris is pretty straight forward:

```scala


object SnatchSomeService {

  import co.horn.Boris.RestClientFactory
  
  // List of identical redundant servers
  val uri = Seq(Uri("https://server.one"), Uri("https://server.two"))
  
  // Create a client for a round robin pool of servers using a pooled connection to each server
  val client = RestClientFactory.multiPoolClient(uri)
  
  // Perform the request on the next server in the list
  val res = client.exec(Get("/some/service")).map(r ⇒ Unmarshal(r.entity).to[String])
}
```

## Connection types
Boris can maintain three different connection types. These are:

* Multi-server pooled connections: Boris maintains a pooled connection to each server in the pool
(subject to Akka HTTP's pool settings) and issues requests to the servers in a Round Robin scheduler. 
Failed requests are sent to the next server in the list. Repeated failures on a server result in that
server being removed from the pool for a fixed period of time.
* Single-server pooled connections: This is a wrapper over Akka HTTP's pooled connection to a single 
server.
* Single-server one connection per request: In cases where a pool is not practical, Boris can
issue single http requests.

Each of these connection types allows management of the entity returned by the request. Because
the underlying transport is an Akka Stream and must be dealt with as such. Either the server 
must send a `Connection: close` in the header or the caller needs to attach some Sink (e.g. 
`Sink.ignore` or an `Unmarshal`) to consume the stream. Failure to do so will result in a leak
of the stream which results in a visit to StackOverflow. Boris provides variants of the `exec` 
method which either request the stream to fully consume the response entity (`execStrict`) or
drop the entity (`execDrop`) if you have no interest in the entity.

For pooled clients, Boris provides a range of strategies to deal with overflows on the request
queues. These are the `OverflowStrategy` methods provided by Akka Streams

* Drop head: Drop the oldest request in the queue and enqueue this request.
* Drop tail: Drop the newest element and replace it with this request.
* Drop buffer: Clear everything in the queue and submit this request.
* Drop new: If the buffer is full, drop this request.
* Backpressure: Apply backpressure upstream.
* Fail: Complete the stream with a failure.

## Configuration ##

The default configuration for Boris is given below. The configurations can be overridden
in various ways using the methods in `RestClientFactory`. There are two separate configurations. 
One specifies the connection pool configuration (Akka's `ConnectionPoolSettings`) and the other configures
Boris (`BorisConfig`). The Boris configuration parameters are presented below.

```hocon
horn.boris {
  name = "boris_rest_client"        // Sets the name of the Stream Source
  bufferSize = 100                  // Default size of the http-client queue.
  overflowStrategy = "dropNew"      // "dropHead", "dropTail", "dropBuffer", "dropNew", "backpressure" or  "fail"
  request-timeout = 10s             // The stream will time out a request after this time
  materialize-timeout = 8s          // Time out for waiting on the entity when using "strictExec()"

  dead-server {                     // Dead server management
    enabled = true                  // Enable removal of the dead server if it fails repeatedly
    failure-threshold = 5           // The number of consecutive failures before the server is considered as failed
    suspend-time = 30s              // Maintain the failed status for this interval before retrying the server
    min-available-servers = 1       // If fewer than this number of servers is available, fail the request
  }
}
```



# Contribution policy #

Contributions via GitHub pull requests are gladly accepted from their original
author. Along with any pull requests, please state that the contribution is your
original work and that you license the work to the project under the project's
open source license. Whether or not you state this explicitly, by submitting any
copyrighted material via pull request, email, or other means you agree to
license the material under the project's open source license and warrant that
you have the legal authority to do so.

# License #

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
