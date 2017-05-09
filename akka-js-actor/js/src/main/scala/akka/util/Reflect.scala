/**
 * Copyright (C) 2009-2015 Typesafe Inc. <http://www.typesafe.com>
 */
package akka.util

import scala.util.control.NonFatal
import scala.collection.immutable
import scala.annotation.tailrec
import scala.util.Try
import scala.scalajs.js

/**
 *
 * INTERNAL API
 */
private[akka] object Reflect {

  protected[akka] final def lookupAndSetField(clazz: Class[_], instance: AnyRef, name: String, value: Any): Boolean = {
    //Now we manage only IR patched classes
    import akka.actor._

    type PatchedActorCell = {
      var props: Props
    }

    type PatchedActor = {
      var context: ActorContext
      var self: ActorRef
    }

    try {
      name match {
        case "props" =>
          instance.asInstanceOf[PatchedActorCell].props = value.asInstanceOf[Props]
          true
        case "context" =>
          instance.asInstanceOf[PatchedActor].context = value.asInstanceOf[ActorContext]
          true
        case "self" =>
          instance.asInstanceOf[PatchedActor].self = value.asInstanceOf[ActorRef]
          true
        case any =>
          false
      }
    } catch {
      case err : Throwable =>
        false
    }
  }

  /**
   * INTERNAL API
   * @param clazz the class which to instantiate an instance of
   * @return a function which when applied will create a new instance from the default constructor of the given class
   */
  private[akka] def instantiator[T](clazz: Class[T]): () ⇒ T = () ⇒ instantiate(clazz)

  /**
   * INTERNAL API
   * @param clazz the class which to instantiate an instance of
   * @return a new instance from the default constructor of the given class
   */
  private[akka] def instantiate[T](clazz: Class[T]): T =
    try instantiate[T](findConstructor(clazz, immutable.Seq[Any]()), immutable.Seq[Any]())
    catch {
      case iae: IllegalAccessException ⇒
        throw new IllegalArgumentException(s"cannot instantiate actor", iae)
    }

  /**
   * INTERNAL API
   * Calls findConstructor and invokes it with the given arguments.
   */
  private[akka] def instantiate[T](clazz: Class[T], args: immutable.Seq[Any]): T = {
    instantiate(findConstructor(clazz, args), args)
  }

  import scala.scalajs.reflect._
  /**
   * INTERNAL API
   * Invokes the constructor with the given arguments.
   */
  private[akka] def instantiate[T](constructor: InvokableConstructor, args: immutable.Seq[Any]): T = {
    try
      constructor.newInstance(args: _*).asInstanceOf[T]
    catch {
      case e: IllegalArgumentException ⇒
        val argString = args mkString ("[", ", ", "]")
        throw new IllegalArgumentException(s"constructor $constructor is incompatible with arguments $argString", e)
    }
  }

  /**
   * INTERNAL API
   * Implements a primitive form of overload resolution a.k.a. finding the
   * right constructor.
   */
  private[akka] def findConstructor[T](clazz: Class[T], args: immutable.Seq[Any]): InvokableConstructor = {
    val ctor = scala.scalajs.reflect.Reflect.lookupInstantiatableClass(clazz.getName).getOrElse {
      throw new InstantiationError(s"Reflect $clazz is not js instantiable class")
    }
    ctor.declaredConstructors.find(_.parameterTypes.size == args.size).getOrElse{
      throw new InstantiationError(s"Reflect $clazz is not instantiable with provided args")
    }
  }

  val getCallerClass: Option[Int ⇒ Class[_]] = None

  private[akka] def findClassLoader(): ClassLoader = null

}
