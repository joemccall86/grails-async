package grails.async

import grails.async.decorator.PromiseDecorator
import org.grails.async.factory.future.CachedThreadPoolPromiseFactory
import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.lang.Specification

import java.util.concurrent.ExecutionException

/**
 * Created by graemerocher on 29/03/2017.
 */
class FutureTaskPromiseFactorySpec extends Specification {
    void setup() {
        Promises.promiseFactory = new CachedThreadPoolPromiseFactory()
    }

    void cleanup() {
        Promises.promiseFactory = null
    }

    void "Test add promise decorator"() {
        when:"A decorator is added"
        def decorator = { Closure c ->
            return { "*${c.call(*it)}*" }
        } as PromiseDecorator

        def p = Promises.createPromise( { 10 }, [decorator] )
        def result = p.get()

        then:"The result is decorate"
        result == "*10*"
    }

    void "Test promise map handling"() {
        when:"A promise map is created"
        def map = Promises.createPromise(one: { 1 }, two: { 1 + 1 }, four:{2 * 2})
        def result = map.get()

        then:"The map is valid"
        result == [one: 1, two: 2, four: 4]
    }

    void "Test promise list handling"() {
        when:"A promise list is created from two promises"
        def p1 = Promises.createPromise { 1 + 1 }
        def p2 = Promises.createPromise { 2 + 2 }
        def list = Promises.createPromise(p1, p2)

        def result
        list.onComplete { List v ->
            result = v
        }

        sleep 200
        then:"The result is correct"
        result == [2,4]

        when:"A promise list is created from two closures"
        list = Promises.createPromise({ 1 + 1 }, { 2 + 2 })

        list.onComplete { List v ->
            result = v
        }

        sleep 200
        then:"The result is correct"
        result == [2,4]
    }

    void "Test promise onComplete handling"() {

        when:"A promise is executed with an onComplete handler"
        def promise = Promises.createPromise { 1 + 1 }
        def result
        def hasError = false
        promise.onComplete { val ->
            result = val
        }.get()
        promise.onError {
            hasError = true
        }.get()

        then:"The onComplete handler is invoked and the onError handler is ignored"
        result == 2
        hasError == false
    }

    @IgnoreIf({ System.getenv("TRAVIS")})
    void "Test promise onError handling"() {

        when:"A promise is executed with an onComplete handler"
        def promise = Promises.createPromise {
            throw new RuntimeException("bad")
        }
        def result
        Throwable error
        promise.onComplete { val ->
            result = val
        }
        promise.onError { err ->
            error = err
        }.get()

        then:"The onComplete handler is invoked and the onError handler is ignored"
        thrown(ExecutionException)
        result == null
        error != null
    }

    void "Test promise chaining"() {
        when:"A promise is chained"
        def promise = Promises.createPromise { 1 + 1 }
        promise = promise.then { it * 2 } then { it + 6 }
        def val = promise.get()

        then:'the chain is executed'
        val == 10
    }

    void "Test promise chaining with exception"() {
        when:"A promise is chained"
        def promise = Promises.createPromise { 1 + 1 }
        promise = promise.then { it * 2 } then { throw new RuntimeException("bad")} then { it + 6 }
        def val = promise.get()

        then:'the chain is executed'
        thrown RuntimeException
        val == null
    }


    @Issue("GRAILS-10152")
    void "Test promise closure is not executed multiple times if it returns null"() {
        given:
        Closure callable =  Mock(Closure) {
            call() >> null
        }

        when:"A promise is created"
        Promises.waitAll([Promises.createPromise(callable), Promises.createPromise(callable)])

        then:'the closure is executed twice'
        2 * callable.call()
    }
}
