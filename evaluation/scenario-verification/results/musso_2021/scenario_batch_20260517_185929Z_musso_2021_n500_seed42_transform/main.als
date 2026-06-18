module main

open world_structure[World]
open ontological_properties[World]
open util/relation
open util/sequniv
open util/ternary

abstract sig Endurant {}

sig Object extends Endurant {}

sig Aspect extends Endurant {}

sig Datatype {}

sig Date in Datatype {
        day: one Number,
        month: one Number,
        year: one Number
}

sig Number in Datatype {}

abstract sig World {
        exists: some Endurant,
        Organization: set exists:>Object,
        Employment: set exists:>Aspect,
        Employee: set exists:>Object,
        ActiveOrganization: set exists:>Object,
        InactiveOrganization: set exists:>Object,
        Skill: set exists:>Aspect,
        Person: set exists:>Object,
        SocialEntity: set exists:>Endurant,
        salary: set Employment set -> one Number,
        birthDate: set Person set -> one Date,
        hires: set Organization -> Employment -> Employee,
        inheresin: set Skill set -> one Employee,
        relation: set Employment some -> one Employee,
        relation1: set Employment some -> one Organization
} {
        exists:>Object in Organization+Person
        exists:>Aspect in Employment+Skill
        disj[Employment,Organization,Person,Skill]
}

fact additionalFacts {
        continuous_existence[exists]
        elements_existence[Endurant,exists]
}

fact relationProperties {
        immutable_target[Skill,inheresin]
        immutable_target[Employment,relation]
        immutable_target[Employment,relation1]
}

fact rigid {
        rigidity[Organization,Object,exists]
}

fact rigid {
        rigidity[Employment,Aspect,exists]
}

fact relatorConstraint {
        all w: World, x: w.Employment | #(Employee1[x,w]+Organization1[x,w])>=2
}

fact rigid {
        rigidity[Skill,Aspect,exists]
}

fact rigid {
        rigidity[Person,Object,exists]
}

fact rigid {
        rigidity[SocialEntity,Endurant,exists]
}

fact additionalDatatypeFacts {
        Datatype = Date+Number
        disj[Date,Number]
}

fact generalization {
        ActiveOrganization in Organization
}

fact generalization {
        InactiveOrganization in Organization
}

fact generalization {
        Employee in Person
}

fact generalization {
        Employment in SocialEntity
}

fact generalization {
        Organization in SocialEntity
}

fact generalizationSet {
        disj[ActiveOrganization,InactiveOrganization]
        Organization = ActiveOrganization+InactiveOrganization
}

fact multiplicity {
        all w: World, x: w.Employee | #Organization2[x,w]>=1
}

fact multiplicity {
        all w: World, x: w.Organization | #Employee2[x,w]>=0
}

fact derivation {
        all w: World, x: w.Organization, y: w.Employee, r: w.Employment | 
            x -> r -> y in w.hires iff x in r.(w.relation1) and y in r.(w.relation)
}

fact acyclic {
        all w: World | acyclic[w.relation,w.Employment]
}

fact acyclic {
        all w: World | acyclic[w.relation1,w.Employment]
}

fact acyclicCharacterizations {
        all w: World | acyclic[(w.inheresin),(w.Skill)]
}

fun visible : World->univ {
        exists+select13[salary]+select13[birthDate]
}

fun salary1 [x: World.Employment, w: World] : set Number {
        x.(w.salary)
}

fun birthDate1 [x: World.Person, w: World] : set Date {
        x.(w.birthDate)
}

fun Organization2 [x: World.Employee, w: World] : set World.Organization {
        (select13[w.hires]).x
}

fun Employee2 [x: World.Organization, w: World] : set World.Employee {
        x.(select13[w.hires])
}

fun Skill1 [x: World.Employee, w: World] : set World.Skill {
        (w.inheresin).x
}

fun Employee3 [x: World.Skill, w: World] : set World.Employee {
        x.(w.inheresin)
}

fun Employment1 [x: World.Employee, w: World] : set World.Employment {
        (w.relation).x
}

fun Employee1 [x: World.Employment, w: World] : set World.Employee {
        x.(w.relation)
}

fun Employment2 [x: World.Organization, w: World] : set World.Employment {
        (w.relation1).x
}

fun Organization1 [x: World.Employment, w: World] : set World.Organization {
        x.(w.relation1)
}

-- Suggested run predicates
run singleWorld for 10 but 1 World, 7 Int
run linearWorlds for 10 but 3 World, 7 Int
run multipleWorlds for 10 but 4 World, 7 Int
run singleWorld for 20 but 1 World, 7 Int
run linearWorlds for 20 but 3 World, 7 Int
run multipleWorlds for 20 but 4 World, 7 Int
