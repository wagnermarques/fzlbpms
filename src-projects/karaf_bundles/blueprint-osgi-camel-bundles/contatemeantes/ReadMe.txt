Camel Router Project for Blueprint (OSGi)
=========================================

To build this project use

    mvn install

To run the project you can execute the following Maven goal

    mvn camel-karaf:run

To deploy in this stack's Karaf container, install straight from the
bind-mounted source tree (the mvn: scheme does not resolve here — see
README.org for why):

    bundle:install -s file:/opt/karaf/deploy_bundles/blueprint-osgi-camel-bundles/contatemeantes/target/contatemeantes-1.0-SNAPSHOT.jar

For more help see the Apache Camel documentation

    http://camel.apache.org/
