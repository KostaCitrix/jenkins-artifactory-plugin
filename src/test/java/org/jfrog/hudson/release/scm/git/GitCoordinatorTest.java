package org.jfrog.hudson.release.scm.git;

import java.io.IOException;
import java.io.PrintStream;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import hudson.EnvVars;
import hudson.model.BuildListener;
import hudson.model.Result;
import org.codehaus.plexus.util.StringOutputStream;
import org.easymock.EasyMockSupport;
import org.eclipse.jgit.lib.ObjectId;
import org.jfrog.hudson.release.ReleaseAction;
import org.jfrog.hudson.release.ReleaseRepository;
import org.jfrog.hudson.release.scm.BuildWrapper;
import org.junit.Test;

import static org.easymock.EasyMock.anyString;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;

public class GitCoordinatorTest extends EasyMockSupport {

    String checkoutBranch = "origin/build-branch";
    String checkoutBranch_short = "build-branch";
    EnvVars buildEnv = new EnvVars("GIT_BRANCH", checkoutBranch);
    PrintStream buildLogger = new PrintStream(new StringOutputStream(), true);
    ObjectId checkoutRevision = ObjectId.fromString("cafecafecafecafecafecafecafecafecafecafe");

    ReleaseAction releaseAction = mock(ReleaseAction.class);
    BuildListener buildListener = mock(BuildListener.class);
    BuildWrapper build = mock(BuildWrapper.class);
    StandardCredentials gitCredentials = mock(StandardCredentials.class);
    ReleaseRepository releaseRepository = mock(ReleaseRepository.class);

    // it's very important when we push, commit etc., that's why gitManager is a _strict_ mock,
    // i.e. if we call a method we did not expect, an exception is being thrown.
    GitManager gitManager = strictMock(GitManager.class);

    // class under test - subclass so that we can inject the scmManager
    GitCoordinator cut = new GitCoordinator(build, buildListener, releaseAction) {
        @Override
        protected void createScmManager() {
            this.scmManager = gitManager;
        }
    };

    class TestRun {
        boolean releaseVersionModified;
        boolean devVersionModified;
        String releaseBranchName;
        String tagName;
        Result result;


        private void setupExpectation() throws Exception {
            // common initialization
            expect(build.getEnvironment(buildListener)).andReturn(buildEnv);
            expect(buildListener.getLogger()).andReturn(buildLogger).anyTimes();
            // setup release action interactions
            expect(releaseAction.getReleaseBranch()).andReturn(releaseBranchName);
            expect(releaseAction.getGitCredentials()).andReturn(gitCredentials);
            expect(releaseAction.isCreateReleaseBranch()).andReturn(releaseBranchName != null).anyTimes();
            expect(releaseAction.isCreateVcsTag()).andReturn(tagName != null).anyTimes();
            final String tagCommitMessage = "bla tag bla";
            expect(releaseAction.getTagComment()).andReturn(tagCommitMessage).anyTimes();
            final String devVersionCommitMessage = "next dev version";
            expect(releaseAction.getNextDevelCommitComment()).andReturn(devVersionCommitMessage).anyTimes();
            final String remoteName = "git://foobar";
            expect(releaseAction.getTargetRemoteName()).andReturn(remoteName).anyTimes();
            expect(gitManager.getRemoteConfig(anyString())).andStubReturn(releaseRepository);

            gitManager.setGitCredentials(gitCredentials);
            expectLastCall();

            // prepare
            expect(gitManager.revParse(checkoutBranch)).andReturn(checkoutRevision);

            // beforeReleaseVersionChange
            if (releaseBranchName != null) {
                gitManager.checkoutBranch(releaseBranchName, true);
                expectLastCall();
            } else {
                gitManager.checkoutBranch(checkoutBranch, false);
                expectLastCall();
            }

            // afterReleaseVersionChange
            gitManager.commitWorkingCopy(tagCommitMessage);
            expectLastCall();
            if (tagName != null) {
                expect(releaseAction.getTagUrl()).andReturn(tagName);
                gitManager.createTag(tagName, tagCommitMessage);
            }

            // beforeDevelopmentVersionChange
            if (releaseBranchName != null) {
                gitManager.checkoutBranch(checkoutBranch_short, false);
            }

            // afterDevelopmentVersionChange
            if (devVersionModified) {
                gitManager.commitWorkingCopy(devVersionCommitMessage);
            }

            // buildCompleted
            expect(build.getResult()).andReturn(result);
            if (result == Result.SUCCESS) {
                if (releaseBranchName != null) {
                    gitManager.push(releaseRepository, releaseBranchName);
                }
                gitManager.push(releaseRepository, checkoutBranch_short);
            }
        }

        /**
         * Calls all steps of the release plugin, in order
         */
        private void run(GitCoordinator cut) throws Exception {
            cut.prepare();
            cut.beforeReleaseVersionChange();
            cut.afterReleaseVersionChange(releaseVersionModified);
            cut.afterSuccessfulReleaseVersionBuild();
            cut.beforeDevelopmentVersionChange();
            cut.afterDevelopmentVersionChange(devVersionModified);
            cut.buildCompleted();
        }
    }

    @Test
    public void noReleaseBranch_branchAndTagSuccess() throws Exception {
        TestRun testRun = new TestRun();
        testRun.releaseBranchName = "release-v2.1";
        testRun.releaseVersionModified = true;
        testRun.devVersionModified = true;
        testRun.tagName = "v2.1";
        testRun.result = Result.SUCCESS;
        testRun.setupExpectation();

        replayAll();
        testRun.run(cut);
        verifyAll();
    }

}