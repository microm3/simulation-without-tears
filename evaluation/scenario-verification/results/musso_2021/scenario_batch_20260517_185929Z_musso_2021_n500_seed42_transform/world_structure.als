module world_structure[World]

some abstract sig TemporalWorld extends World {
        next: set TemporalWorld -- Immediate next moments
} {
        this not in this.^(@next) -- There are no temporal cicles
        lone ((@next).this) -- A world can be the immediate next momment of at maximum one world
}

one sig CurrentWorld extends TemporalWorld {} {
        next in FutureWorld
}

sig PastWorld extends TemporalWorld {} {
        next in (PastWorld + CounterfactualWorld + CurrentWorld)
        CurrentWorld in this.^@next -- All past worlds can reach the current moment
}

sig FutureWorld extends TemporalWorld {} {
        next in FutureWorld
        this in CurrentWorld.^@next -- All future worlds can be reached by the current moment
}

sig CounterfactualWorld extends TemporalWorld {} {
        next in CounterfactualWorld
        this in PastWorld.^@next -- All past worlds can reach the counterfactual moment
}

-- Elements cannot die and come to life later
pred continuous_existence [exists: World->univ] {
        all w : World, x: (@next.w).exists | (x not in w.exists) => (x not in (( w. ^next).exists))
}

-- All elements must exists in at least one world
pred elements_existence [elements: univ, exists: World->univ] {
        all x: elements | some w: World | x in w.exists
}

-- Run predicate for a single World
pred singleWorld {
        #World=1
}

-- Run predicate for linear Worlds (Past, Current, Future)
pred linearWorlds {
        #World=3 and #PastWorld=1 and #FutureWorld=1
}

-- Run predicate for multiple Worlds (Past, Counterfactual, Current, Future)
pred multipleWorlds {
        #World=4 and #PastWorld=1 and #CounterfactualWorld=1 and #FutureWorld=1
}
