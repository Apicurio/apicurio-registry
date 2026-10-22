package io.apicurio.registry.storage.decorator;

import io.apicurio.registry.storage.RegistryStorage;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Creates a {@link java.lang.reflect.Proxy} that implements {@link RegistryStorage} and routes each method
 * call only through decorators that actually override it, skipping decorators that don't care about the
 * method. This eliminates the need for a base class with ~400 lines of boilerplate delegation.
 */
public final class RegistryStorageProxyFactory {

    private RegistryStorageProxyFactory() {
    }

    /**
     * Creates a proxy implementing {@link RegistryStorage} that chains the given decorators (in order) on top
     * of the raw storage. For each method, only decorators that declare (override) that method are included in
     * the chain.
     * <p>
     * The chain is built at creation time: each decorator receives a delegate proxy that routes overridden
     * methods to the next decorator in the chain and all other methods to the raw storage.
     *
     * @param rawStorage the underlying storage implementation
     * @param decorators the decorators in execution order (lowest order first)
     * @return a proxy that routes calls through the appropriate decorator chain
     */
    public static RegistryStorage createProxy(RegistryStorage rawStorage,
            List<RegistryStorageDecorator> decorators) {
        if (decorators.isEmpty()) {
            return rawStorage;
        }

        // For each decorator, compute which RegistryStorage methods it overrides
        Map<RegistryStorageDecorator, Set<Method>> overriddenMethods = new HashMap<>();
        for (RegistryStorageDecorator decorator : decorators) {
            Set<Method> methods = new java.util.HashSet<>();
            for (Method m : RegistryStorage.class.getMethods()) {
                if (declaresMethod(decorator.getClass(), m)) {
                    methods.add(m);
                }
            }
            overriddenMethods.put(decorator, methods);
        }

        // Build delegate chain from back to front.
        // For the last decorator, delegate = rawStorage.
        // For each preceding decorator, delegate = a proxy that routes overridden methods
        // to the next decorator in the sub-chain and everything else to rawStorage.
        //
        // The "sub-chain" for a decorator at position i consists of decorators [i+1..n-1]
        // that override the method being called.

        // First, set up each decorator's delegate.
        // A decorator's delegate must be a RegistryStorage that, for any method M:
        //   - If any decorator after this one overrides M, routes to the FIRST such decorator
        //   - Otherwise, routes to rawStorage
        for (int i = decorators.size() - 1; i >= 0; i--) {
            RegistryStorageDecorator decorator = decorators.get(i);
            List<RegistryStorageDecorator> remaining = decorators.subList(i + 1, decorators.size());
            RegistryStorage delegateProxy = createDelegateProxy(rawStorage, remaining, overriddenMethods);
            decorator.setDelegate(delegateProxy);
        }

        // The top-level proxy routes each method to the first decorator that overrides it,
        // or directly to rawStorage if none do.
        Map<Method, RegistryStorageDecorator> firstDecorators = new HashMap<>();
        for (Method m : RegistryStorage.class.getMethods()) {
            for (RegistryStorageDecorator decorator : decorators) {
                if (overriddenMethods.get(decorator).contains(m)) {
                    firstDecorators.put(m, decorator);
                    break;
                }
            }
        }

        return (RegistryStorage) Proxy.newProxyInstance(RegistryStorage.class.getClassLoader(),
                new Class<?>[] { RegistryStorage.class }, (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return handleObjectMethod(method, rawStorage, args);
                    }
                    RegistryStorageDecorator first = firstDecorators.get(method);
                    if (first != null) {
                        return invokeUnwrapped(method, first, args);
                    }
                    return invokeUnwrapped(method, rawStorage, args);
                });
    }

    /**
     * Creates a delegate proxy for a decorator. This proxy routes each method to the first decorator in
     * {@code remaining} that overrides it, or to rawStorage if none do.
     */
    private static RegistryStorage createDelegateProxy(RegistryStorage rawStorage,
            List<RegistryStorageDecorator> remaining,
            Map<RegistryStorageDecorator, Set<Method>> overriddenMethods) {
        if (remaining.isEmpty()) {
            return rawStorage;
        }

        Map<Method, RegistryStorageDecorator> routing = new HashMap<>();
        for (Method m : RegistryStorage.class.getMethods()) {
            for (RegistryStorageDecorator decorator : remaining) {
                if (overriddenMethods.get(decorator).contains(m)) {
                    routing.put(m, decorator);
                    break;
                }
            }
        }

        if (routing.isEmpty()) {
            return rawStorage;
        }

        return (RegistryStorage) Proxy.newProxyInstance(RegistryStorage.class.getClassLoader(),
                new Class<?>[] { RegistryStorage.class }, (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return handleObjectMethod(method, rawStorage, args);
                    }
                    RegistryStorageDecorator target = routing.get(method);
                    if (target != null) {
                        return invokeUnwrapped(method, target, args);
                    }
                    return invokeUnwrapped(method, rawStorage, args);
                });
    }

    static boolean declaresMethod(Class<?> clazz, Method interfaceMethod) {
        // Walk the class hierarchy up to (but not including) RegistryStorageDecoratorBase
        Class<?> current = clazz;
        while (current != null && current != Object.class
                && current != RegistryStorageDecoratorBase.class) {
            try {
                current.getDeclaredMethod(interfaceMethod.getName(), interfaceMethod.getParameterTypes());
                return true;
            } catch (NoSuchMethodException e) {
                current = current.getSuperclass();
            }
        }
        return false;
    }

    private static Object invokeUnwrapped(Method method, Object target, Object[] args) throws Throwable {
        try {
            // If the target implements the method's declaring class (e.g. RegistryStorage),
            // we can invoke directly. Otherwise, look up the method on the target's class.
            Method targetMethod;
            if (method.getDeclaringClass().isInstance(target)) {
                targetMethod = method;
            } else {
                targetMethod = target.getClass().getMethod(method.getName(), method.getParameterTypes());
            }
            return targetMethod.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static Object handleObjectMethod(Method method, Object target, Object[] args) throws Throwable {
        switch (method.getName()) {
            case "toString":
                return "RegistryStorageProxy[" + target.toString() + "]";
            case "hashCode":
                return System.identityHashCode(target);
            case "equals":
                return target == args[0];
            default:
                return invokeUnwrapped(method, target, args);
        }
    }
}
