export interface TopicOptions {
    distinctUntilChanged?: boolean;
}

/**
 * A subscription to a topic.  Used to unsubscribe.
 */
export class TopicSubscription<T> {

    private topic: Topic<T>;
    private subscriber: TopicSubscriber<T>;

    constructor(topic: Topic<T>, subscriber: TopicSubscriber<T>) {
        this.topic = topic;
        this.subscriber = subscriber;
    }

    /**
     * Called to unsubscribe from the topic.  The consumer will no longer receive any messages/values.
     */
    unsubscribe(): void {
        this.topic.unsubscribe(this.subscriber);
    }
}

/**
 * A subscriber.
 */
export class TopicSubscriber<T> {

    private callback: (value: T) => void;

    constructor(callback: (value: T) => void) {
        this.callback = callback;
    }

    /**
     * Sends a value to this subscriber.
     */
    send(value: T): void {
        this.callback(value);
    }
}

/**
 * A simple class that handles pub-sub with multiple consumers and multiple producers.  This differs from
 * an rxjs Subject/Observable in that subscribers are not sent the "current" value of the Topic when they
 * first subscribe.  Consumers are only notified of a value when that value is sent.  This, of course, leads
 * to the problem that a consumer must make sure to subscribe to the Topic before a producer sends a value.
 */
export class Topic<T> {

    private options: TopicOptions;
    private subscribers: TopicSubscriber<T>[] = [];
    private currentValue: T;

    constructor(options: TopicOptions = {}) {
        this.options = options;
    }

    /**
     * Gets the current value in the topic.
     */
    getValue(): T {
        return this.currentValue;
    }

    /**
     * Send a value to all subscribers/consumers of the topic.
     */
    send(value: T): void {
        if (this.options.distinctUntilChanged && this.currentValue === value) {
            return;
        }
        this.subscribers.forEach(subscriber => {
            subscriber.send(value);
        });
        this.currentValue = value;
    }

    /**
     * Called by a consumer to subscribe to the topic.  Returns a subscription that can be used to
     * unsubscribe.
     */
    subscribe(next: (value: T) => void): TopicSubscription<T> {
        const subscriber = new TopicSubscriber<T>(next);
        this.subscribers.push(subscriber);
        return new TopicSubscription<T>(this, subscriber);
    }

    /**
     * Called to remove a subscriber from the list of current subscribers.
     */
    unsubscribe(subscriber: TopicSubscriber<T>): void {
        const idx = this.subscribers.indexOf(subscriber);
        if (idx !== -1) {
            this.subscribers.splice(idx, 1);
        }
    }
}
