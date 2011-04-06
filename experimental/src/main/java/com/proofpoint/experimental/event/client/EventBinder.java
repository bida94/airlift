package com.proofpoint.experimental.event.client;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.inject.Binder;
import com.google.inject.Key;

import java.util.List;

import static com.google.inject.util.Types.newParameterizedType;
import static com.proofpoint.experimental.event.client.EventTypeMetadata.getEventTypeMetadata;

public class EventBinder
{
    public static EventBinder eventBinder(Binder binder)
    {
        return new EventBinder(binder);
    }

    private final Binder binder;

    private EventBinder(Binder binder)
    {
        this.binder = binder;
    }

    public <T> void bindEventClient(Class<T> type)
    {
        bindGenericEventClient(type, type);
    }

    public <T> void bindGenericEventClient(Class<T> boundType, Class<? extends T>... eventTypes)
    {
        Preconditions.checkNotNull(boundType, "boundType is null");
        Preconditions.checkNotNull(eventTypes, "eventTypes is null");
        bindGenericEventClient(boundType, ImmutableList.copyOf(eventTypes));
    }

    public <T> void bindGenericEventClient(Class<T> boundType, List<Class<? extends T>> eventTypes)
    {
        Preconditions.checkNotNull(boundType, "boundType is null");
        Preconditions.checkNotNull(eventTypes, "eventTypes is null");

        Binder sourcedBinder = binder.withSource(getCaller());

        // Build event type metadata and bind any errors into Guice
        ImmutableList.Builder<EventTypeMetadata<? extends T>> builder = ImmutableList.builder();
        for (Class<? extends T> eventType : eventTypes) {
            EventTypeMetadata<? extends T> eventTypeMetadata = getEventTypeMetadata(eventType);
            builder.add(eventTypeMetadata);
            for (String error : eventTypeMetadata.getErrors()) {
                sourcedBinder.addError(error);
            }
        }
        EventClientProvider<T> eventClientProvider = new EventClientProvider<T>(builder.build());

        // create a valid key
        Key<EventClient<T>> key = (Key<EventClient<T>>) Key.get(newParameterizedType(EventClient.class, boundType));

        // bind the event client provider
        sourcedBinder.bind(key).toProvider(eventClientProvider);
    }

    private static StackTraceElement getCaller()
    {
        // find the caller of this class to report source
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        boolean foundThisClass = false;
        for (StackTraceElement element : stack) {
            if (!foundThisClass) {
                if (element.getClassName().equals(EventBinder.class.getName())) {
                    foundThisClass = true;
                }
            }
            else {
                if (!element.getClassName().equals(EventBinder.class.getName())) {
                    return element;
                }

            }
        }
        return null;
    }
}