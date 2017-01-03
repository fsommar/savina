package edu.rice.habanero.actors

import java.util.concurrent.atomic.AtomicBoolean

import lacasa.{Box, CanAccess, Safe}
import lacasa.akka.{SafeActor, SafeActorRef}
import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import edu.rice.hj.runtime.util.ModCountDownLatch

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Promise}
import scala.util.Properties._
import scala.util.{Failure, Success}

/**
 *
 * @author Fredrik Sommar (fsommar@kth.se)
 */
abstract class SafeAkkaActor[MsgType] extends SafeActor[MsgType] {
  private val startTracker = new AtomicBoolean(false)
  private val exitTracker = new AtomicBoolean(false)

  final def receive(box: Box[MsgType])(implicit acc: CanAccess { type C = box.C }): Unit = {
    box open {
      case msg: StartAkkaActorMessage =>
        if (hasStarted()) {
          msg.resolve(value = false)
        } else {
          start()
          msg.resolve(value = true)
        }

      case msg: Any =>
        if (!exitTracker.get()) {
          process(msg.asInstanceOf[MsgType])
        }
    }
  }

  def process(msg: MsgType): Unit

  final def hasStarted() = {
    startTracker.get()
  }

  final def start() = {
    if (!hasStarted()) {
      onPreStart()
      onPostStart()
      startTracker.set(true)
    }
  }

  /**
   * Convenience: specify code to be executed before actor is started
   */
  protected def onPreStart() = {
  }

  /**
   * Convenience: specify code to be executed after actor is started
   */
  protected def onPostStart() = {
  }

  final def hasExited() = {
    exitTracker.get()
  }

  final def exit() = {
    val success = exitTracker.compareAndSet(false, true)
    if (success) {
      onPreExit()
      context.stop(self)
      onPostExit()
      SafeAkkaActorState.actorLatch.countDown()
    }
  }

  /**
   * Convenience: specify code to be executed before actor is terminated
   */
  protected def onPreExit() = {
  }

  /**
   * Convenience: specify code to be executed after actor is terminated
   */
  protected def onPostExit() = {
  }
}

protected class StartSafeAkkaActorMessage(val promise: Promise[Boolean] = Promise()) {
  def await() {
    Await.result(promise.future, Duration.Inf)
  }

  def resolve(value: Boolean) {
    promise.success(value)
  }
}

object SafeAkkaActorState {

  implicit val promiseOfBooleanIsSafe = new Safe[Promise[Boolean]] {}

  val actorLatch = new ModCountDownLatch(0)

  private val mailboxTypeKey = "actors.mailboxType"
  private var config: Config = null

  def setPriorityMailboxType(value: String) {
    System.setProperty(mailboxTypeKey, value)
  }

  def initialize(): Unit = {

    val corePoolSize = getNumWorkers("actors.corePoolSize", 4)
    val maxPoolSize = getNumWorkers("actors.maxPoolSize", corePoolSize)
    val priorityMailboxType = getStringProp(mailboxTypeKey, "akka.dispatch.SingleConsumerOnlyUnboundedMailbox")

    val customConfigStr = """
    akka {
      log-dead-letters-during-shutdown = off
      log-dead-letters = off

      actor {
        creation-timeout = 6000s
        default-dispatcher {
          fork-join-executor {
            parallelism-min = %s
            parallelism-max = %s
            parallelism-factor = 1.0
          }
        }
        default-mailbox {
          mailbox-type = "akka.dispatch.SingleConsumerOnlyUnboundedMailbox"
        }
        prio-dispatcher {
          mailbox-type = "%s"
        }
        typed {
          timeout = 10000s
        }
      }
    }
                          """.format(corePoolSize, maxPoolSize, priorityMailboxType)
    val customConf = ConfigFactory.parseString(customConfigStr)
    config = ConfigFactory.load(customConf)

  }

  private def getNumWorkers(propertyName: String, minNumThreads: Int): Int = {
    val rt: Runtime = java.lang.Runtime.getRuntime

    getIntegerProp(propertyName) match {
      case Some(i) if i > 0 => i
      case _ => {
        val byCores = rt.availableProcessors() * 2
        if (byCores > minNumThreads) byCores else minNumThreads
      }
    }
  }


  private def getIntegerProp(propName: String): Option[Int] = {
    try {
      propOrNone(propName) map (_.toInt)
    } catch {
      case _: SecurityException | _: NumberFormatException => None
    }
  }

  private def getStringProp(propName: String, defaultVal: String): String = {
    propOrElse(propName, defaultVal)
  }

  def newActorSystem(name: String): ActorSystem = {
    ActorSystem(name, config)
  }

  def startActor[T >: StartSafeAkkaActorMessage](actorRef: SafeActorRef[T]) {
    SafeAkkaActorState.actorLatch.updateCount()

    Box.mkBox[StartSafeAkkaActorMessage] { packed =>
      implicit val access = packed.access
      val promise = packed.box.extract(_.promise)

      actorRef.tellContinue(packed.box) { () =>
        val f = promise.future
        f.onComplete {
          case Success(value) =>
            if (!value) {
              SafeAkkaActorState.actorLatch.countDown()
            }
          case Failure(e) => e.printStackTrace
        }
      }
    }
  }

  def awaitTermination(system: ActorSystem) {
    try {
      actorLatch.await()
      system.terminate()
    } catch {
      case ex: InterruptedException => {
        ex.printStackTrace()
      }
    }
  }
}
