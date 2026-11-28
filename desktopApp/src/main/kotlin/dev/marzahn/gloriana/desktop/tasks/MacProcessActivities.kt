package dev.marzahn.gloriana.desktop.tasks

import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer

private const val ALL_USER_INITIATED_OPTIONS = 0x00FFFFFFL
private const val IDLE_SYSTEM_SLEEP_DISABLED = 1L shl 20
private const val USER_INITIATED_ALLOWING_IDLE_SLEEP = ALL_USER_INITIATED_OPTIONS and IDLE_SYSTEM_SLEEP_DISABLED.inv()

class MacProcessActivities : ProcessActivities {
  private val runtime = NativeLibrary.getInstance("objc")
  private val send = runtime.getFunction("objc_msgSend")
  private val selector = runtime.getFunction("sel_registerName")
  private val findClass = runtime.getFunction("objc_getClass")
  private val pushPool = runtime.getFunction("objc_autoreleasePoolPush")
  private val popPool = runtime.getFunction("objc_autoreleasePoolPop")
  private val processInfo by lazy { message(type("NSProcessInfo"), "processInfo") }

  init {
    NativeLibrary.getInstance("Foundation")
  }

  override fun begin(reason: String): ProcessActivity {
    val pool = pushPool.invokePointer(emptyArray())
    val activity = try {
      val text = message(type("NSString"), "stringWithUTF8String:", reason)
      val begun = message(processInfo, "beginActivityWithOptions:reason:", USER_INITIATED_ALLOWING_IDLE_SLEEP, text)
      message(begun, "retain")
    } finally {
      popPool.invokeVoid(arrayOf(pool))
    }
    return MacActivity(activity)
  }

  internal inner class MacActivity(val handle: Pointer) : ProcessActivity {
    override fun end() {
      command(processInfo, "endActivity:", handle)
      command(handle, "release")
    }
  }

  private fun type(name: String): Pointer =
    checkNotNull(findClass.invokePointer(arrayOf(name))) { "No Objective-C class $name" }

  private fun message(receiver: Pointer, name: String, vararg arguments: Any): Pointer =
    checkNotNull(send.invokePointer(call(receiver, name, arguments))) { "$name returned nil" }

  private fun command(receiver: Pointer, name: String, vararg arguments: Any) {
    send.invokeVoid(call(receiver, name, arguments))
  }

  private fun call(receiver: Pointer, name: String, arguments: Array<out Any>): Array<Any> =
    arrayOf(receiver, selector.invokePointer(arrayOf(name)), *arguments)
}
