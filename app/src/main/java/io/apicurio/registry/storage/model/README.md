# Apicurio Registry storage data model

The central concept is that of an `artifact`, which represents 
a sequence of stored files.

Each file in the sequence is an `artifact version`. 
The sequence is accepted or rejected based on the policy set by the active rules,
and *ordered* by its creation time.

[TODO]
