package org.jfrog.hudson.release.scm;

import java.io.IOException;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.model.TaskListener;

/**
 * The methods actually used in ScmCoordinator & ScmManager of hudson.model.AbstractBuild
 * Using the actual Jenkins classes, writing Unit tests was infeasible
 */
public interface JenkinsBuild<P extends AbstractProject<P,R>,R extends AbstractBuild<P,R>> {

    Result getResult();

    EnvVars getEnvironment(TaskListener log) throws IOException, InterruptedException;

    AbstractBuild<P,R> getBuild();
}
