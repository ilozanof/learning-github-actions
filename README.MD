# learning-github-actions

This is a Proof Of Concept of the use of GitHub Actions and its interactions 
with AWS.

## First Step: Create the Project.

First we create a GitHub repo in our Account (this Repo)

Then we initialize the maven project using the mvn command line:

```
mvn archetype:generate -DgroupId=com.ilozanof.learning.github -DartifactId=actions -DarchetypeArtifactId=
maven-archetype-quickstart -DinteractiveMode=false
```

The App is a very simple Rest Endpoint using *Quarkus*. So first we define the dependencies in 
our *pom.xml* :

```
<properties>
    ...
    <quarkus.platform.group-id>io.quarkus.platform</quarkus.platform.group-id>
    <quarkus.platform.version>3.2.3.Final</quarkus.platform.version>
    <quarkus.platform.artifact-id>quarkus-bom</quarkus.platform.artifact-id>
    ...
  </properties>
  ...
  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>io.quarkus.platform</groupId>
        <artifactId>${quarkus.platform.artifact-id}</artifactId>
        <version>${quarkus.platform.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>
  ...
  <dependencies>
    ...
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-resteasy-reactive-jackson</artifactId>
    </dependency>
  </dependencies>
  ...
  <build>
    <plugins>
      ...
      <plugin>
        <groupId>${quarkus.platform.group-id}</groupId>
        <artifactId>quarkus-maven-plugin</artifactId>
        <version>${quarkus.platform.version}</version>
        <extensions>true</extensions>
        <executions>
          <execution>
            <goals>
              <goal>build</goal>
              <goal>generate-code</goal>
              <goal>generate-code-tests</goal>
            </goals>
          </execution>
        </executions>
      </plugin>
      ...
    </plugins>
  </build>
```

Our Rest Point is just a *GET* Endpoint that returns a "Hello World" in JSON Format.

```
@Path("")
public class HelloWorld {

    @GET
    @Path("/helloWorld")
    @Produces(MediaType.APPLICATION_JSON)
    public String helloWorld() {
        return "Hello World!";
    }
}
```

We can run it now in our IDE and check that it works fine with a Browser.

## Set up the First Workflow, to build, test and deploy the artifact to our GitHib Repo

### Setup in AWS

This what we need to set up in the AWS Side:

* Create a ECR Repository in some region
* Create a User ("learning-github-actions-user", for example) using IAM.
* Assign a *Policy* to this user that allows his to Pull/Push images to the ECR Repository. You can assign the "EC2InstanceProfileForImageBuilderECRContainerBuilds" policy.
* Create an AccessKey for this User. Write down both *Access Key* and *Secret Access Key*

### Setup in Github Repo

The workflow defined uses the credentials of the user above to connect to ECR. So we need to set up those credentials somewhere
where the workflow Runner can access them. In this case we save them as *Secrets* of our Repo. So we define 2 Secrets:
* AWS_ACCESS_KEY_ID
* AWS_SECRET_ACCESS_KEY

These 2 secrets are referenced by the Workflow.

## Setup in the Java project

Since we are deploying the artifact to GitHub repo, we need to add a *distributionManagement* section in the 
*pom.xml* with the definition of the Server:

```
<distributionManagement>
    <repository>
      <id>GitHub-repo</id>
      <name>GitHub-repo</name>
      <url>https://maven.pkg.github.com/ilozanof/learning-github-actions</url>
    </repository>
  </distributionManagement>
```
The *id* attribute here will be referenced later in the Git Workflow.

The Workflow is defined in `/.github/workflows/build.yml`. This workflow takes care of checking out the code, 
building it, testing it, deploying it to GitHub packages, and then building a docker Image and pushing it to our 
AWS ECR repository.

Now we create a *Dockerfile* to build our image:

```
FROM openjdk:17
MAINTAINER Ivan Lozano
WORKDIR .
COPY target/quarkus-app/lib lib/
COPY target/quarkus-app/app app/
COPY target/quarkus-app/quarkus quarkus/
COPY target/quarkus-app/quarkus-run.jar app.jar
ENTRYPOINT ["java","-jar", "/app.jar"]
```

The important thing about this *Dockerfile* is that in a *Quarkus* app, we need to copy not only the *app.jar* generated but also
everything in the folders specified above.

We can check this runs ok:
```
docker build -t learning-github-actions .
docker container run -d -p 8080:8080 -it learning-github-actions
```


After the workflow runs, we should be able to see the image published in our ECR Repo.

## Set up a workflow to UPLOAD a docker-compose file to a EC2 instance and running it there.

Now we'll define *another* workflow that will run *after* the first workflow has finished successfully. 
This new workflow is implemented in `deploy.yml` and its triggered after the
previous `build.yml` has completed successfully. This new workflow will:

* Upload the docker-compose file to our EC2 instance
* Connect to our instance using SSH and the SSH Key previously downloaded (and used now as a *Secret*)
* Install all the software we need in the EC2 instance: docker and docker-compose
* Execute the docker-compose file, whihc will pull the image from our ECR repository

### Setup in AWS

We need to set up a EC2 instance, the minimal configuration eligible for the free tier will do.

Then, we need to create a SSH Key in order to connect to this instance via *SSH*. We need to download this SSH Key and save
it locally.

We also need to set up a *Elastic Address* and assign it to the previous instance.

### Setup in the GitHub Repo

We need to set up *Secrets* to store both the IP Address of the EC2 instance and the SSH Key:

* AWS_EC2_IP
* AWS_EC2_PRIVATE_KEY

### Setup in the Java project

We create a *docker-compose.yml*, this compose file will be copied into a AWS EC2 and it will *pull* the image 
from the ECR repository where the previous workflow had pushed it:
```
version: "3.7"

services:
helloworld:
image: 521221046117.dkr.ecr.us-east-1.amazonaws.com/learning-github-actions:latest
container_name: learning-github-actions
ports:
- "8080:8080"
```

Notice that we need to specify our Repository URL in the docker-compose.

Now we can run it from Local, to see if it actually pulls the image from our AWS Repo. Pulling images from AWS ECR 
required authentication, so before we run *docker compose* we need to authenticate ourselves to AWS ECR. We can do that 
by going to the ECR Repo in AWS console and click on the "View push commands" and then we copy and paste the command in 
our shell:

```
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin 521221046117.dkr.ecr.us-east-1.amazonaws.com
```

After that, we can run the Docker-compose file:
```
docker-compose -f docker-compose.yml up -d
```

The previous steps are only for checking locally that everything works. The final goal is to make this all work in CI

> **NOTE:**
> At this moment, this *push.yml* flow is incomplete. Pending thing to do are:
> * Executing the docker-compose file after connecting to the EC2 instance from the GitHub action fails with a "no basic auth credentials". The funny thing is that if we do the same with the commend line as the HitHub action, it wors fine.
> * The EC2 instance needs to be configured properly, so after running our image we can access it via browser. So we need to set up the 
> * security groups and the rules to allow HTTP traffic.

# Troubles Summary

### Trouble: [SSL validation failed]

If when running the command we copied from AWS console we get this error:

*SSL validation failed for https://api.ecr.us-east-1.amazonaws.com/ [SSL: CERTIFICATE_VERIFY_FAILED] certificate verify failed: unable to get local issuer certificate (_ssl.c:1002)
Error: Cannot perform an interactive login from a non TTY device*

, then this means that our local *aws cli* has a problme witht he certifcates. The *aws cli*^is written in *Python*, so in order to fix this we need to to 
the following:

* Go to AWS Console using a browser
* Check out where the certificates for the Pythong interpreter are in your machine:
```
python3 -m certifi
```

This will show an absolute Path:
```
/usr/local/lib/python3.11/site-packages/certifi/cacert.pem
```

* Set up the Environment Variable *AWS_CA_BUNDLE* to this path:
```
export $AWS_CA_BUNDLE=/usr/local/lib/python3.11/site-packages/certifi/cacert.pem
```
Now run the Command from AWS again.