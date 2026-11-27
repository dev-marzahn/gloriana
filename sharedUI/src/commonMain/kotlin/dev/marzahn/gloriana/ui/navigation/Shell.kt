package dev.marzahn.gloriana.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import dev.marzahn.gloriana.ui.components.GlorianaSnackbarHost
import dev.marzahn.gloriana.ui.messages.AppMessages
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import dev.marzahn.gloriana.ui.theme.LookMotion
import dev.marzahn.gloriana.ui.theme.Navigation

private val PushDistance = 30.dp

@Composable
internal fun Shell(
  navigator: ShellNavigator,
  entries: Map<Destination, List<NavEntry<NavKey>>>,
  hasSystemBack: Boolean,
  messages: AppMessages,
  modifier: Modifier = Modifier,
) {
  CompositionLocalProvider(LocalShellNavigator provides navigator) {
    val navigation = GlorianaTheme.widthClass.navigation
    val shellModifier = modifier.fillMaxSize().background(GlorianaTheme.colors.canvas)
    val besideRail = @Composable { rail: @Composable () -> Unit ->
      Row(shellModifier) {
        rail()
        val insets = ShellInsets.besideRail
        DestinationContent(navigator, entries, navigation, messages, Modifier.weight(1f).windowInsetsPadding(insets))
      }
    }
    when (navigation) {
      Navigation.Bar -> Column(shellModifier) {
        val insets = if (navigator.atRoot) ShellInsets.topLevelScreen else ShellInsets.safeDrawingWithoutKeyboard
        DestinationContent(navigator, entries, navigation, messages, Modifier.weight(1f).windowInsetsPadding(insets))
        if (navigator.atRoot) GlorianaNavigationBar(navigator.current, navigator::select)
      }
      Navigation.LabelledRail -> besideRail { LabelledNavigationRail(navigator.current, navigator::select) }
      Navigation.Workbench -> besideRail { WorkbenchRail(navigator.current, navigator::select) }
    }
  }
  NavigationBackHandler(
    state = rememberNavigationEventState(NavigationEventInfo.None),
    isBackEnabled = hasSystemBack && navigator.canLeaveToHome,
    onBackCompleted = navigator::leaveToHome,
  )
}

@Composable
private fun DestinationContent(
  navigator: ShellNavigator,
  entries: Map<Destination, List<NavEntry<NavKey>>>,
  navigation: Navigation,
  messages: AppMessages,
  modifier: Modifier = Modifier,
) {
  val push = rememberPushTransitions()
  val motion = GlorianaTheme.motion
  val sceneStrategies = if (navigation == Navigation.Workbench) listOf(ListDetailSceneStrategy) else emptyList()
  Box(modifier) {
    AnimatedContent(
      targetState = navigator.current,
      modifier = Modifier.fillMaxSize(),
      transitionSpec = { fadeIn(motion.fadeThroughIn.tween()) togetherWith fadeOut(motion.fadeThroughOut.tween()) },
    ) { destination ->
      NavDisplay(
        entries = entries.getValue(destination),
        sceneStrategies = sceneStrategies,
        transitionSpec = push.forward,
        popTransitionSpec = push.backward,
        predictivePopTransitionSpec = { push.scrubbedBackward(this) },
        onBack = { navigator.pop(destination) },
      )
    }
    MessagesHost(messages, navigator, Modifier.align(Alignment.BottomCenter))
  }
}

@Composable
private fun MessagesHost(messages: AppMessages, navigator: ShellNavigator, modifier: Modifier = Modifier) {
  val hostState = remember { SnackbarHostState() }
  LaunchedEffect(messages, navigator) {
    messages.messages.collect { message ->
      val result = hostState.showSnackbar(message.text, message.action?.label)
      if (result == SnackbarResult.ActionPerformed) message.action?.perform(navigator)
    }
  }
  GlorianaSnackbarHost(hostState, modifier)
}

private class PushTransitions(distancePx: Int, direction: LayoutDirection, motion: LookMotion) {
  private val towardEnd = if (direction == LayoutDirection.Ltr) distancePx else -distancePx
  private val enter = motion.enter
  private val exit = motion.exit
  private val linear: FiniteAnimationSpec<IntOffset> = tween(motion.exit.durationMillis, easing = LinearEasing)
  private val linearFade: FiniteAnimationSpec<Float> = tween(motion.exit.durationMillis, easing = LinearEasing)

  val forward: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
    (slideInHorizontally(enter.tween()) { towardEnd } + fadeIn(enter.tween())) togetherWith
      (slideOutHorizontally(exit.tween()) { -towardEnd } + fadeOut(exit.tween()))
  }

  val backward: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
    (slideInHorizontally(enter.tween()) { -towardEnd } + fadeIn(enter.tween())) togetherWith
      (slideOutHorizontally(exit.tween()) { towardEnd } + fadeOut(exit.tween()))
  }

  val scrubbedBackward: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
    (slideInHorizontally(linear) { -towardEnd } + fadeIn(linearFade)) togetherWith
      (slideOutHorizontally(linear) { towardEnd } + fadeOut(linearFade))
  }
}

@Composable
private fun rememberPushTransitions(): PushTransitions {
  val distancePx = with(LocalDensity.current) { PushDistance.roundToPx() }
  val direction = LocalLayoutDirection.current
  val motion = GlorianaTheme.motion
  return remember(distancePx, direction, motion) { PushTransitions(distancePx, direction, motion) }
}
