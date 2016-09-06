package org.jfrog.hudson.release.scm;

import java.io.IOException;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.model.TaskListener;

/**
 * Wrap the hudson.model.AbstractBuild type, exposing the methods actually used in
 * ScmCoordinator & ScmManager. The idea is to make classes using AbstractBuild easier
 * to unit-test as the AbstractBuild class does very werid things in its initialization.
 */
public interface BuildWrapper<P extends AbstractProject<P,R>,R extends AbstractBuild<P,R>> {

    Result getResult();

    EnvVars getEnvironment(TaskListener log) throws IOException, InterruptedException;

    AbstractBuild<P,R> getBuild();
}
