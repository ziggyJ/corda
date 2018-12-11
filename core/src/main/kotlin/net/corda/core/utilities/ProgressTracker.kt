package net.corda.core.utilities

import net.corda.core.internal.STRUCTURAL_STEP_PREFIX
import net.corda.core.serialization.CordaSerializable
import rx.Observable
import rx.Subscription
import rx.subjects.PublishSubject
import rx.subscriptions.Subscriptions
import kotlin.reflect.KProperty

/**
 * A progress tracker helps surface information about the progress of an operation to a user interface or API of some
 * kind. It lets you define a set of _steps_ that represent an operation. A step is represented by an object (typically
 * a singleton).
 *
 * Steps may logically be children of other steps, which models the case where a large top level operation involves
 * sub-operations which may also have a notion of progress. If a step has children, then the tracker will report the
 * steps children as the "next step" after the parent. In other words, a parent step is considered to involve actual
 * reportable work and is a thing. If the parent step simply groups other steps, then you'll have to step over it
 * manually.
 *
 * Each step has a label. It is assumed by default that the label does not change. If you want a label to change, then
 * you can emit a [ProgressTracker.Change.Rendering] object on the [ProgressTracker.Step.changes] observable stream
 * after it changes. That object will propagate through to the top level trackers [changes] stream, which renderers can
 * subscribe to in order to learn about progress.
 *
 * An operation can move both forwards and backwards through steps, thus, a [ProgressTracker] can represent operations
 * that include loops.
 *
 * A progress tracker is *not* thread safe. You may move events from the thread making progress to another thread by
 * using the [Observable] subscribeOn call.
 */
@CordaSerializable // oh no it isn't
class ProgressTracker(vararg inputSteps: Step) {

    @CordaSerializable
    sealed class Change(val progressTracker: ProgressTracker) {
        data class Position(val tracker: ProgressTracker, val newStep: Step) : Change(tracker) {
            override fun toString() = newStep.label
        }

        data class Rendering(val tracker: ProgressTracker, val ofStep: Step) : Change(tracker) {
            override fun toString() = ofStep.label
        }

        data class Structural(val tracker: ProgressTracker, val parent: Step) : Change(tracker) {
            override fun toString() = STRUCTURAL_STEP_PREFIX + parent.label
        }
    }

    /** The superclass of all step objects. */
    @CordaSerializable
    open class Step(open val label: String) {
        open val changes: Observable<Change> get() = Observable.empty()
        open fun childProgressTracker(): ProgressTracker? = null
        /**
         * A flow may populate this property with flow specific context data.
         * The extra data will be recorded to the audit logs when the flow progresses.
         * Even if empty the basic details (i.e. label) of the step will be recorded for audit purposes.
         */
        open val extraAuditData: Map<String, String> get() = emptyMap()
    }

    // Sentinel objects. Overrides equals() to survive process restarts and serialization.
    object UNSTARTED : Step("Unstarted") {
        override fun equals(other: Any?) = other is UNSTARTED
    }

    object STARTING : Step("Starting") {
        override fun equals(other: Any?) = other is STARTING
    }

    object DONE : Step("Done") {
        override fun equals(other: Any?) = other is DONE
    }

    private val publisher by transient { ProgressPublisher() }

    private val stepList = arrayOf(UNSTARTED, STARTING, *inputSteps, DONE).toList()

    private val stepStateEvents: StepStateEvents = object : StepStateEvents {
        override fun onStructuralChange(affectedStep: Step) {
            publisher.structuralChange(this@ProgressTracker, affectedStep)
            rebuildStepsTree()
        }

        override fun onRewind(newIndex: Int) {
            // We are going backwards: unlink and unsubscribe from any child nodes that we're rolling back
            // through, in preparation for moving through them again.
            for (i in stepIndex downTo newIndex) {
                childProgressTrackers.removeAndDetach(steps[i])
            }
        }

        override fun onStepChange(change: Change) {
            publisher.change(change)
            if (change is Change.Structural || change is Change.Rendering) rebuildStepsTree() else recalculateStepsTreeIndex()
        }

        override fun onNewStep(newStep: Step) {
            publisher.newPosition(this@ProgressTracker, newStep)
            recalculateStepsTreeIndex()
        }

        private fun rebuildStepsTree() {
            stepHierarchy.recalculate()
            publisher.newStepsTree(allStepsLabels)

            recalculateStepsTreeIndex()
        }

        private fun recalculateStepsTreeIndex() {
            val step = currentStepRecursiveWithoutUnstarted()
            stepsTreeIndex = stepHierarchy.indexOf(step)
        }
    }

    private val childProgressTrackers = ChildProgressTrackers(stepStateEvents, publisher)

    private val stepHierarchy = StepHierarchy(stepList) { step ->
        getChildProgressTracker(step)?.allSteps?.asSequence() ?: emptySequence()
    }

    private val stepState = StepState(stepStateEvents, stepList, publisher)

    /** The steps in this tracker, same as the steps passed to the constructor but with UNSTARTED and DONE inserted. */
    val steps get() = stepList.toTypedArray()

    /**
     * An observable stream of changes: includes child steps, resets and any changes emitted by individual steps (e.g.
     * if a step changed its label or rendering).
     */
    val changes: Observable<Change> get() = publisher.changes

    /**
     * An observable stream of changes to the [allStepsLabels]
     */
    val stepsTreeChanges: Observable<List<Pair<Int, String>>> get() = publisher.stepsTreeChanges

    /**
     * An observable stream of changes to the [stepsTreeIndex]
     */
    val stepsTreeIndexChanges: Observable<Int> get() = publisher.stepsTreeIndexChanges

    /** Returns true if the progress tracker has ended, either by reaching the [DONE] step or prematurely with an error */
    val hasEnded: Boolean get() = publisher.hasEnded

    var currentStep: Step by stepState

    init {
        steps.forEach {
            val childTracker = it.childProgressTracker()
            if (childTracker != null) {
                setChildProgressTracker(it, childTracker)
            }
        }
        this.currentStep = UNSTARTED
    }

    /** The zero-based index of the current step in the [steps] array (i.e. with UNSTARTED and DONE) */
    val stepIndex: Int get() = stepState.currentIndex

    /** The zero-bases index of the current step in a [allStepsLabels] list */
    var stepsTreeIndex: Int = -1
        private set(value) {
            field = value
            publisher.newStepsTreeIndex(value)
        }

    /**
     * Reading returns the value of steps[stepIndex], writing moves the position of the current tracker. Once moved to
     * the [DONE] state, this tracker is finished and the current step cannot be moved again.
     */

    /** Returns the current step, descending into children to find the deepest step we are up to. */
    val currentStepRecursive: Step
        get() = getChildProgressTracker(currentStep)?.currentStepRecursive ?: currentStep

    /** Returns the current step, descending into children to find the deepest started step we are up to. */
    private val currentStartedStepRecursive: Step
        get() {
            val step = getChildProgressTracker(currentStep)?.currentStartedStepRecursive ?: currentStep
            return if (step == UNSTARTED) currentStep else step
        }

    private fun currentStepRecursiveWithoutUnstarted(): Step {
        val stepRecursive = getChildProgressTracker(currentStep)?.currentStartedStepRecursive
        return if (stepRecursive == null || stepRecursive == UNSTARTED) currentStep else stepRecursive
    }

    fun getChildProgressTracker(step: Step): ProgressTracker? = childProgressTrackers.get(step)

    fun setChildProgressTracker(step: ProgressTracker.Step, childProgressTracker: ProgressTracker) {
        childProgressTrackers.set(this, step, childProgressTracker)
    }

    /**
     * Ends the progress tracker with the given error, bypassing any remaining steps. [changes] will emit the exception
     * as an error.
     */
    fun endWithError(error: Throwable) {
        publisher.endWithError(error)
    }

    /** The parent of this tracker: set automatically by the parent when a tracker is added as a child */
    var parent: ProgressTracker? = null
        internal set

    /** Walks up the tree to find the top level tracker. If this is the top level tracker, returns 'this' */
    @Suppress("unused") // TODO: Review by EOY2016 if this property is useful anywhere.
    val topLevelTracker: ProgressTracker
        get() = this.parent?.topLevelTracker ?: this

    /**
     * A list of all steps in this ProgressTracker and the children, with the indent level provided starting at zero.
     * Note that UNSTARTED is never counted, and DONE is only counted at the calling level.
     */
    val allSteps: List<Pair<Int, Step>> get() = stepHierarchy.allSteps

    /**
     * A list of all steps label in this ProgressTracker and the children, with the indent level provided starting at zero.
     * Note that UNSTARTED is never counted, and DONE is only counted at the calling level.
     */
    val allStepsLabels: List<Pair<Int, String>> get() = stepHierarchy.allStepsLabels()

    /**
     * Iterates the progress tracker. If the current step has a child, the child is iterated instead (recursively).
     * Returns the latest step at the bottom of the step tree.
     */
    fun nextStep(): Step = stepState.nextStep()

    companion object {
        val DEFAULT_TRACKER = { ProgressTracker() }
    }
}
// TODO: Expose the concept of errors.
// TODO: It'd be helpful if this class was at least partly thread safe.

private class ProgressPublisher {

    val changes: PublishSubject<ProgressTracker.Change> = PublishSubject.create()
    val stepsTreeChanges: PublishSubject<List<Pair<Int, String>>> = PublishSubject.create()
    val stepsTreeIndexChanges: PublishSubject<Int> = PublishSubject.create()

    val hasEnded: Boolean get() = changes.hasCompleted() || changes.hasThrowable()

    fun change(change: ProgressTracker.Change) {
        changes.onNext(change)
    }

    fun error(error: Throwable) {
        changes.onError(error)
    }

    fun newPosition(tracker: ProgressTracker, newStep: ProgressTracker.Step) {
        change(ProgressTracker.Change.Position(tracker, newStep))
    }

    fun complete() {
        changes.onCompleted()
        stepsTreeIndexChanges.onCompleted()
        stepsTreeChanges.onCompleted()
    }

    fun newStepsTreeIndex(newIndex: Int) {
        stepsTreeIndexChanges.onNext(newIndex)
    }

    fun structuralChange(progressTracker: ProgressTracker, step: ProgressTracker.Step) {
        change(ProgressTracker.Change.Structural(progressTracker, step))
    }

    fun endWithError(error: Throwable) {
        check(!hasEnded) { "Progress tracker has already ended" }
        
        changes.onError(error)
        stepsTreeIndexChanges.onError(error)
        stepsTreeChanges.onError(error)
    }

    fun newStepsTree(allStepsLabels: List<Pair<Int, String>>) {
        stepsTreeChanges.onNext(allStepsLabels)
    }
}

private class ProgressSubscriber {

    private var currentSubscription: Subscription = Subscriptions.empty()

    inline fun updateSubscription(newSubscriptionProvider: () -> Subscription) {
        currentSubscription.unsubscribe()
        currentSubscription = newSubscriptionProvider()
    }

}

@CordaSerializable
private interface StepStateEvents {
    fun onRewind(newIndex: Int)
    fun onStepChange(change: ProgressTracker.Change)
    fun onNewStep(newStep: ProgressTracker.Step)
    fun onStructuralChange(affectedStep: ProgressTracker.Step)
}

@CordaSerializable
private class StepState(val events: StepStateEvents, val steps: List<ProgressTracker.Step>, val publisher: ProgressPublisher) {

    var currentIndex: Int = 0
    private var currentStep: ProgressTracker.Step = ProgressTracker.UNSTARTED
    private val subscriber = ProgressSubscriber()

    fun nextStep(): ProgressTracker.Step {
        val newIndex = currentIndex + 1
        require(newIndex < steps.size) { "Cannot move to new step from ${currentStep.label}" }

        val newStep = steps[newIndex]
        changeStepIndex(newIndex, newStep)
        return newStep
    }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): ProgressTracker.Step = currentStep
    operator fun setValue(thisRef: Any?, property: KProperty<*>, newStep: ProgressTracker.Step) {
        check((newStep === ProgressTracker.DONE && publisher.hasEnded) || !publisher.hasEnded) {
            "Cannot rewind a progress tracker once it has ended"
        }
        if (currentStep == newStep) return

        val newIndex = steps.indexOf(newStep)
        require(newIndex != -1) { "Step ${newStep.label} not found in progress tracker." }

        changeStepIndex(newIndex, newStep)
    }

    private fun changeStepIndex(newIndex: Int, newStep: ProgressTracker.Step) {
        if (newIndex < currentIndex) events.onRewind(newIndex)

        currentIndex = newIndex
        currentStep = newStep

        events.onNewStep(currentStep)
        subscriber.updateSubscription {
            currentStep.changes.subscribe(events::onStepChange, publisher::error)
        }

        if (currentStep == ProgressTracker.DONE) {
            publisher.complete()
        }
    }
}

@CordaSerializable
private class ChildProgressTrackers(private val events: StepStateEvents, private val publisher: ProgressPublisher) {

    @CordaSerializable
    private data class Child(val tracker: ProgressTracker, @Transient val subscription: Subscription) {
        fun detachFromParent() {
            tracker.parent = null
            subscription.unsubscribe()
        }
    }

    private val childProgressTrackers = mutableMapOf<ProgressTracker.Step, Child>()

    fun get(step: ProgressTracker.Step): ProgressTracker? = childProgressTrackers[step]?.tracker

    fun set(parent: ProgressTracker, step: ProgressTracker.Step, childProgressTracker: ProgressTracker) {
        childProgressTrackers[step] = Child(
                childProgressTracker,
                childProgressTracker.changes.subscribe(events::onStepChange, publisher::error))
        childProgressTracker.parent = parent

        events.onStructuralChange(step)
    }

    fun removeAndDetach(step: ProgressTracker.Step) {
        childProgressTrackers.remove(step)?.detachFromParent()
        events.onStructuralChange(step)
    }
}

@CordaSerializable
private class StepHierarchy(private val steps: List<ProgressTracker.Step>,
                            private val childStepsProvider: (ProgressTracker.Step) -> Sequence<Pair<Int, ProgressTracker.Step>>) {

    private var allStepsCache: List<Pair<Int, ProgressTracker.Step>> = calculateAllSteps().toList()

    private fun calculateAllSteps(level: Int = 0): Sequence<Pair<Int, ProgressTracker.Step>> =
            steps.asSequence().filterNot { it == ProgressTracker.UNSTARTED || (level >= 0 && it == ProgressTracker.DONE) }
                    .flatMap { step ->
                        sequenceOf(level to step) + childStepsProvider(step)
                    }

    fun allStepsLabels(level: Int = 0): List<Pair<Int, String>> =
            calculateAllSteps(level).map { Pair(it.first, it.second.label) }
                    .toList()

    fun recalculate() {
        allStepsCache = calculateAllSteps().toList()
    }

    fun indexOf(step: ProgressTracker.Step): Int = allStepsCache.indexOfFirst { it.second == step }

    val allSteps get() = allStepsCache
}

