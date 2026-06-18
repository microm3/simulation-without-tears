module ontological_properties[World]

-- This predicate states that a class is rigid
pred rigidity [Class: univ->univ, Nature: univ, exists: univ->univ] {
        all w1: World, p: univ | p in w1.exists and p in w1.Class implies
            all w2: World | w1!=w2 and p in w2.exists implies p in w2.Class
}

-- This predicate states that a class is anti-rigid
pred antirigidity [Class: set univ->univ, Nature: univ, exists: univ->univ] {
        all x: Nature | #World>=2 implies (some disj w1,w2: World |
            x in w1.exists and x in w1.Class and x in w2.exists and x not in w2.Class)
}

-- This predicate makes the source relation end immutable
pred immutable_source [Target: World->univ, rel: univ->univ->univ] {
        all w1: World, x: univ | x in w1.Target implies
            all w2: World | x in w2.Target implies (w1.rel).x=(w2.rel).x
}

-- This predicate makes the target relation end immutable
pred immutable_target [Source: World->univ, rel: univ->univ->univ] {
        all w1: World, x: univ | x in w1.Source implies
            all w2: World | x in w2.Source implies x.(w1.rel)=x.(w2.rel)
}
