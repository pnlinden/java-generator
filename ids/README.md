# Infomodel Codegen Tool
# Ontology to Java
## Overview:
Input:
- Ontology as e.g. turtle files
- optionally SHACL shapes as e.g. turtle files

Output:
- Java classes representing the ontology
- optionally with some specified constrains given by the SHACL shapes

## Preparation
- Create a profile in `ids-infomodel-condegen/pom.xml`:
- Replace `NAME, PATH/TO/ONTOLOGY` with a name for your profile and the path on your local machine to the ontology, you want to use with the *codegen tool* (=CGT).
```
...
<profile>
	<id>NAME</id>
	<properties>
		<infomodel-sourcebase>
			PATH/TO/ONTOLOGY
		</infomodel-sourcebase>
	</properties>
</profile>
...
```

**TODO**: *How to add the ontology namespaces!!!!*

Current assumptions in this step:
- For the Generator, we hard-coded the specific paths within the ontology, that are used for the CGT.
- These paths do not need to be adapted, if used in combination with the IDS-InformationModel.
```
ids-infomodel-codegen/java/pom.xml:
...
<mainClass>de.fraunhofer.iais.eis.generate.Generator</mainClass>
<arguments>
    <argument>-vd</argument><argument>${infomodel-sourcebase}</argument>
    <argument>-pp</argument><argument>**/model/*/*.ttl</argument>
    <argument>-vd</argument><argument>${infomodel-sourcebase}/codes</argument>
    <argument>-pp</argument><argument>**/*.ttl</argument>
    <argument>-vd</argument><argument>${infomodel-sourcebase}/taxonomies</argument>
    <argument>-pp</argument><argument>**/*.ttl</argument>
    <argument>-vd</argument><argument>${infomodel-sourcebase}</argument>
    <argument>-pp</argument><argument>**/testing/*/*.ttl</argument>
    <argument>-td</argument><argument>${tmp-path}/templates</argument>
</arguments>
```
- We also assume that there are SHACL shapes (but not required).

**What happens if**...
- ... one path is invalid -> everything from that path is ignored
- ... a shape for a class is missing -> the most general Java class is generated (see Expectations regarding Ontology)
- ... `rdfs:domain` is missing for a property in the ontology -> property is ignored, i.e. will not be used in any Java class.
- ... `rdfs:range` is missing for a property in the ontology -> **ERROR!** SPARQL queries currently depend on that, has to be adapted to be more robust in the future.

*TODO (IDEA): `initialize` script, that asks for what we need and checks, if that exists?*

## Execution
*Hint: <NAME> is the name previously defined in the profile of the pom.xml.*

In directory `~/ids-infomodel-codegen/` run:
`$ mvn clean package -P <NAME> -pl :java`

## How it works
We query the provided ontology and shapes using **SPARQL**. [...]

### Expectations regarding the Ontology
Consider the following simple example (prefixes omitted for brevity):
```
ex:AbstractMessage a owl:Class .

ex:Message a owl:Class ;
		rdfs:subClassOf ex:AbstractMessage .

ex:MessageContent a owl:Class .

ex:messageContent a owl:ObjectProperty ;
		rdfs:domain ex:Message ;
		rdfs:range ex:MessageContent .
```

The CGT would translate this to Java in the following way:
- for the three classes (`ex:AbstractMessage`, `ex:Message`, `ex:MessageContent`) an interface, a builder and an implementation class would be generated. (e.g. `AbstractMessage.java`, `AbstractMessageBuilder.java`, `AbstractMessageImpl.java`)
- the `Message` class would have the property `messageContent`, which has the type `ArrayList` of `MessageContent`.
- there are some additional generic properties and methods

If the CGT is provided **with SHACL shapes**, the following constraints can be added to these very generic translations:
- **abstract classes** (with `sh:sparql`)
- different maximal cardinalities (**field** -> [0 or 1] **or array** -> [0 or more] with `sh:maxCount`)
- different minimal cardinalities **annotations** (field -> `@NotNull` == *exactly one* or array `@NotEmpty` == *at least 1* with `sh:minCount`)
- create a **field/array of type URI** instead of the linked range for an ObjectProperty in the ontology (`sh:nodeKind`)


### Abstract Classes
Abstract classes are a common concept in the Java developer world, but not in the RDF world. We still wanted to include that option. Therefore, we have explicitly included the following possibility, to generate an abstract class for a RDF class:
  1. have a SHACL shape for the class.
  2. have a SPARQL query, where the message states, `... is an abstract class.`

<=> A valid example to generate an abstract class for ex:AbstractMessage would be the following:
```
shapes:AbstractMessageShape
    a sh:NodeShape ;
    sh:targetClass ex:AbstractMessage ;
    sh:sparql [
        a sh:SPARQLConstraint ;
        sh:message "A ex:AbstractMessage is an abstract class. Please use one of the subclasses for the generation of instances."@en ;
        sh:prefixes shapes: ;
        sh:select """
            SELECT ?this ?type
            WHERE {
                ?this rdf:type ?type .
                FILTER (?type = ex:AbstractMessage)
            }
            """ ;
    ]
```

As result, only one java file (`AbstractMessage.java`) would be created as an interface.

### Maximum Cardinalities - Fields vs Lists
The RDF owl:DatatypeProperty and owl:ObjectProperty properties can be used to link an arbitrary amount of entities from the specified domain with an entity from the specified range. In Java, it makes an important difference, whether there is one piece of information linked to a class or multiple pieces. The CGT decides, if a property becomes a field or an array according to the following guidelines:
- If **no** SHACL-shape exists, it will be an ArrayList.
- If a shape exists, several szenarios are possible:
	- the shape does not include cardinality restrictions via `sh:minCount` and `sh:maxCount` -> create **ArrayList**
	- there is a `sh:minCount <n>` with 0 <= n and no `sh:maxCount` -> create **ArrayList**
	- there is a `sh:minCount <n>` and a `sh:maxCount <m>` with 0 <= n < m -> create **ArrayList**
	- there is a `sh:minCount <n>` with and a `sh:maxCount <m>` with 1 <= n < m -> create **ArrayList**
	- there is a `sh:minCount <n>` with and a `sh:maxCount <m>` with 0 < n <= m = 1 -> create **Field**

=> Only when **explicitly** specified that the **maxCount** is 1 and minCount is less or equal to 1, a field will be created.

For the above mentioned example, an array and a field version could be the following:
1. The `ex:messageContent` property does not have any SHACL validation so far. Therefore, the CGT would use as default the ArrayList representation in Java.
2. To get a field in Java, a similar shape the the following is needed:
```
shapes:MessageShape
		a sh:NodeShape ;
		sh:targetClass ex:Message ;
		sh:property [
				a sh:PropertyShape ;
				sh:path ex:messageContent ;
				sh:class ex:MessageContent ;
				sh:maxCount 1 ;
				sh:severity sh:Violation ;
				sh:message "A ex:messageContent should point from an ex:Message to an ex:MessageContent." ;
		]
```

*Assumption: Let class B be a subclass of class A. Then a property P must be translated a field or an array in __both__ classes in the same way. This means that class A cannot define a maxCount 2 for P while class B defines a maxCount of 1.*

### Minimum Cardinality - Field and List Annotations
It might be necessary, that some properties link an entity to at least *n* (*n >= 1*) other entities. For fields with *n = 1*, the CGT adds the Java Annotation "@NotNull". For arrays with *n >= 1*, the CGT adds the Java Annotation "@NotEmpty" This will be added to the representation of the property in Java, if:
- a SHACL shape exists for the property
- and the shape has a `sh:minCount <n>` with **n >= 1**

For the above mentioned example this could be one of the following:
1. With no SHACL shapes `messageContent` would not be annotated.
2. For an **array** with `@NotEmpty` annotation:
```
shapes:MessageShape
		a sh:NodeShape ;
		sh:targetClass ex:Message ;
		sh:property [
				a sh:PropertyShape ;
				sh:path ex:messageContent ;
				sh:class ex:MessageContent ;
				sh:minCount 1 ;
				sh:severity sh:Violation ;
				sh:message "A ex:messageContent should point from an ex:Message to at least one ex:MessageContent." ;
		]
```
3. For a **field** with `@NotNull`:
```
shapes:MessageShape
		a sh:NodeShape ;
		sh:targetClass ex:Message ;
		sh:property [
				a sh:PropertyShape ;
				sh:path ex:messageContent ;
				sh:class ex:MessageContent ;
				sh:minCount 1 ;
				sh:maxCount 1 ;
				sh:severity sh:Violation ;
				sh:message "A ex:messageContent should point from an ex:Message to exactly one ex:MessageContent." ;
		]
```

*Assumption: Let class B be a subclass of class A. Then a property P must be translated a field or an array in __both__ classes in the same way. This means that class A cannot define a minCount 2 for P while class B defines a maxCount of 1.*

### Reference a Class via URI
Some class instances in RDF can be quite complex. If for example in communication, each message has to include all information about all mentioned instances, this would create a huge overhead. Therefore, it can be useful to only reference some instances via URI instead of providing all necessary RDF triples.

For such cases, it is possible to specify a `rdfs:domain ex:<someClass>` and simultaneously define in SHACL that the property just has to be connected to an IRI.
The CGT translates this to a Java field or array of type `URI`.

If in the example `ex:messageContent` should only link to an URI, it could be expressed in SHACL in the following way:
```
shapes:MessageShape
		a sh:NodeShape ;
		sh:targetClass ex:Message ;
		sh:property [
				a sh:PropertyShape ;
				sh:path ex:messageContent ;
				sh:nodeKinde sh:IRI ;
				sh:severity sh:Violation ;
				sh:message "A ex:messageContent should point from an ex:Message to an IRI reference of an ex:MessageContent." ;
		]
```

As a result, the property would be translated as an ArrayList of URIs.

### Individuals as Java Enums (Text is still WIP)
We consider the following cases that can be specified in the RDF ontology:
1. Basic case where each relevant individual I for a class C is of `rdf:type C`:
```
	ex:Message rdf:type owl:Class.

	ex:HelloWorld rdf:type ex:Message .
```

- This is translated into a Java Enum (simplified version shown here):
```java
	public enum Message {
		HELLOWORLD("https://example.com/HelloWorld");
	}
```

2. Extented basic case where there is a subclass S of a class C and both have individuals:
```
	ex:Message rdf:type owl:Class .

	ex:SMS rdf:type owl:Class ;
		rdfs:subClassOf ex:Message .

	ex:HelloWorld rdf:type ex:Message .

	ex:HelloSmartphone rdf:type ex:SMS .
``` 

- As there is no inheritance in Java Enums, we use all individuals from subclasses as individuals of the superclass:
```java
	public enum Message {
		HELLOWORLD("https://example.com/HelloWorld");
		HELLOSMARTPHONE("https://example.com/HelloSmartphone");
	}

	public enum SMS {
		HELLOSMARTPHONE("https://example.com/HelloSmartphone");
	}
```

3. Special definition of which individuals belong to a class via `owl:oneOf`. This allows to include/exclude certain specified individuals from the class or a subclass because if a list of individuals is specified via `owl:oneOf` only these individuals will be part of the enum. The specified individuals still need to be part of the class or a subclass of that class :
```
	ex:Message rdf:type owl:Class ;
		owl:oneOf (
			ex:HelloWorld
			ex:HelloSmartphone
		) .

	ex:SMS rdf:type owl:Class ;
		rdfs:subClassOf ex:Message .

	ex:HelloWorld rdf:type ex:Message .
	ex:SecretMessage rdf:type ex:Message .
	
	ex:HelloSmartphone rdf:type ex:SMS .
```

- This would lead to the same result as before in case 2 because the third message (`ex:SecretMessage`) is not part of the `owl:oneOf` listing.

### Property name translation to Java name
**TODO / WIP**:
- combine name of property with its domain in the Java name to avoid possible collisions.

### Types and missing types
**TODO**...

### Error Handling
**TODO**...
