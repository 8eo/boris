
name := "boris"
version := "0.0.28"
scalaVersion := "2.11.7"
fork in Test := true

libraryDependencies ++= {
  val akkaV        = "2.4.2"
  val scalaTestV   = "2.2.5"
  val configV      = "1.3.0"
  val slickV       = "3.1.1"
  val sl4jV        = "1.6.4"
  Seq(
    "com.typesafe.akka"   %% "akka-actor"                         % akkaV,
    "com.typesafe.akka"   %% "akka-stream"                        % akkaV,
    "com.typesafe.akka"   %% "akka-http-core"                     % akkaV,
    "com.typesafe.akka"   %% "akka-http-experimental"             % akkaV,
    "com.typesafe.akka"   %% "akka-http-spray-json-experimental"  % akkaV,
    "com.typesafe.akka"   %% "akka-slf4j"                         % akkaV,
    "org.slf4j"           %  "slf4j-nop"                          % sl4jV,
    "com.typesafe.akka"   %% "akka-http-testkit"                  % akkaV,
    "com.typesafe"        %  "config"                             % configV,
    "org.scalatest"       %% "scalatest"                          % scalaTestV    % "test"
  )
}




initialCommands := """|import monadic.boris._
                      |""".stripMargin
