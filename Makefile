test:
	bin/kaocha

target:
	mkdir -p target

target/clj_event_source.jar: target src/clj_event_source/*
	clojure -A:jar

jar: target/clj_event_source.jar

pom.xml:
	clojure -Spom

deploy: pom.xml test target/clj_event_source.jar
	mvn deploy:deploy-file -Dfile=target/clj_event_source.jar -DrepositoryId=clojars -Durl=https://clojars.org/repo -DpomFile=pom.xml

clean:
	rm -fr target

.PHONY: test deploy clean jar
