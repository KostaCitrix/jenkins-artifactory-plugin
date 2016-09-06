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

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.anyString;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;

public class GitCoordinatorTest extends EasyMockSupport {

    final String checkoutBranchWithOrigin = "origin/build-branch";
    final String checkoutBranch = "build-branch";
    final EnvVars buildEnv = new EnvVars("GIT_BRANCH", checkoutBranchWithOrigin);
    final PrintStream buildLogger = new PrintStream(new StringOutputStream(), true);
    final ObjectId checkoutRevision = ObjectId.fromString("cafecafecafecafecafecafecafecafecafecafe");
    final String tagCommitMessage = "bla bla release version";
    final String devVersionCommitMessage = "next dev version";

    final ReleaseAction releaseAction = mock(ReleaseAction.class);
    final BuildListener buildListener = mock(BuildListener.class);
    final BuildWrapper build = mock(BuildWrapper.class);
    final StandardCredentials gitCredentials = mock(StandardCredentials.class);
    final ReleaseRepository releaseRepository = mock(ReleaseRepository.class);

    // it's very important when we push, commit etc., that's why gitManager is a _strict_ mock,
    // i.e. if we call a method we did not expect, an exception is being thrown.
    final GitManager gitManager = strictMock(GitManager.class);

    // class under test - subclass so that we can inject the scmManager
    final GitCoordinator cut = new GitCoordinator(build, buildListener, releaseAction) {
        @Override
        protected void createScmManager() {
            this.scmManager = gitManager;
        }
    };

    class TestParams {
        boolean releaseVersionModified;
        boolean devVersionModified;
        String releaseBranchName;
        String tagName;
        Result result;

        private void setupBasicExpectations() throws Exception {
            // common initialization
            expect(build.getEnvironment(buildListener)).andReturn(buildEnv);
            expect(buildListener.getLogger()).andReturn(buildLogger).anyTimes();

            // setup release action interactions
            expect(releaseAction.getReleaseBranch()).andReturn(releaseBranchName);
            expect(releaseAction.getGitCredentials()).andReturn(gitCredentials);
            expect(releaseAction.isCreateReleaseBranch()).andReturn(releaseBranchName != null).anyTimes();
            expect(releaseAction.isCreateVcsTag()).andReturn(tagName != null).anyTimes();
            expect(releaseAction.getTagComment()).andReturn(tagCommitMessage).anyTimes();
            expect(releaseAction.getTagUrl()).andReturn(tagName).anyTimes();
            expect(releaseAction.getNextDevelCommitComment()).andReturn(devVersionCommitMessage).anyTimes();
            final String remoteName = "git://foobar";
            expect(releaseAction.getTargetRemoteName()).andReturn(remoteName).anyTimes();
            expect(build.getResult()).andReturn(result);

            expect(gitManager.getRemoteConfig(anyString())).andStubReturn(releaseRepository);
            gitManager.setGitCredentials(gitCredentials);
            expectLastCall();
            expect(gitManager.revParse(checkoutBranchWithOrigin)).andReturn(checkoutRevision);

            if (result != Result.SUCCESS) {
                //if the build is not successful, make sure there is never any git push
                gitManager.push(anyObject(ReleaseRepository.class), anyString());
                expectLastCall().andStubThrow(new AssertionError("Expected no git push in case of unsuccessful build"));
            }
        }
    }

    private void expectPushCheckoutBranch() throws Exception {
        gitManager.push(releaseRepository, checkoutBranch);
    }

    private void expectPushReleaseBranch(TestParams testParams) throws Exception {
        gitManager.push(releaseRepository, testParams.releaseBranchName);
    }

    private void expectCreateTag(TestParams testParams, String tagCommitMessage) throws IOException, InterruptedException {
        gitManager.createTag(testParams.tagName, tagCommitMessage);
    }

    private void expectCommitWorkingCopy(String tagCommitMessage) throws IOException, InterruptedException {
        gitManager.commitWorkingCopy(tagCommitMessage);
        expectLastCall();
    }

    private void expectCheckoutCheckoutBranch() throws IOException, InterruptedException {
        gitManager.checkoutBranch(checkoutBranch, false);
        expectLastCall();
    }

    void expectCheckoutReleaseBranch(TestParams testParams) throws IOException, InterruptedException {
        gitManager.checkoutBranch(testParams.releaseBranchName, true);
        expectLastCall();
    }

    private void expectFailureRecovery(TestParams testParams,
                                       DELETE_RELEASE_BRANCH deleteReleaseBranch,
                                       DELETE_TAG deleteTag)
            throws IOException, InterruptedException {

        expectCheckoutCheckoutBranch();
        if (deleteReleaseBranch == DELETE_RELEASE_BRANCH.YES) {
            gitManager.deleteLocalBranch(testParams.releaseBranchName);
        }
        if (deleteTag == DELETE_TAG.YES) {
            gitManager.deleteLocalTag(testParams.tagName);
        }
        gitManager.revertWorkingCopyTo(checkoutBranch, checkoutRevision.name());
    }

    /**
     * Calls all steps of the release plugin, in order
     */
    private void run(TestParams testParams) throws Exception {
        replayAll();
        cut.prepare();
        cut.beforeReleaseVersionChange();
        cut.afterReleaseVersionChange(testParams.releaseVersionModified);
        cut.afterSuccessfulReleaseVersionBuild();
        cut.beforeDevelopmentVersionChange();
        cut.afterDevelopmentVersionChange(testParams.devVersionModified);
        cut.buildCompleted();
        verifyAll();
    }

    @Test
    public void releaseBranch_AndTag_Success() throws Exception {
        TestParams testParams = new TestParams();
        testParams.releaseBranchName = "release-v2.1";
        testParams.tagName = "v2.1";
        testParams.releaseVersionModified = true;
        testParams.devVersionModified = true;
        testParams.result = Result.SUCCESS;
        testParams.setupBasicExpectations();

        expectCheckoutReleaseBranch(testParams);
        expectCommitWorkingCopy(tagCommitMessage);
        expectCreateTag(testParams, tagCommitMessage);
        expectCheckoutCheckoutBranch();
        expectCommitWorkingCopy(devVersionCommitMessage);
        expectPushReleaseBranch(testParams);
        expectPushCheckoutBranch();

        run(testParams);
    }

    @Test
    public void releaseBranch_AndTag_noVersionChanges_Success() throws Exception {
        TestParams testParams = new TestParams();
        testParams.releaseBranchName = "release-v2.1";
        testParams.tagName = "v2.1";
        testParams.releaseVersionModified = false;
        testParams.devVersionModified = false;
        testParams.result = Result.SUCCESS;
        testParams.setupBasicExpectations();

        expectCheckoutReleaseBranch(testParams);
        expectCreateTag(testParams, tagCommitMessage);
        expectCheckoutCheckoutBranch();
        expectPushReleaseBranch(testParams);

        run(testParams);
    }

    @Test
    public void releaseBranch_AndTag_onlyReleaseVersionChanges_Success() throws Exception {
        TestParams testParams = new TestParams();
        testParams.releaseBranchName = "release-v2.1";
        testParams.tagName = "v2.1";
        testParams.releaseVersionModified = true;
        testParams.devVersionModified = false;
        testParams.result = Result.SUCCESS;
        testParams.setupBasicExpectations();

        expectCheckoutReleaseBranch(testParams);
        expectCommitWorkingCopy(tagCommitMessage);
        expectCreateTag(testParams, tagCommitMessage);
        expectCheckoutCheckoutBranch();
        expectPushReleaseBranch(testParams);

        run(testParams);
    }

    @Test
    public void releaseBranch_AndTag_onlyDevVersionChanges_Success() throws Exception {
        TestParams testParams = new TestParams();
        testParams.releaseBranchName = "release-v2.1";
        testParams.tagName = "v2.1";
        testParams.releaseVersionModified = false;
        testParams.devVersionModified = true;
        testParams.result = Result.SUCCESS;
        testParams.setupBasicExpectations();

        expectCheckoutReleaseBranch(testParams);
        expectCreateTag(testParams, tagCommitMessage);
        expectCheckoutCheckoutBranch();
        expectCommitWorkingCopy(devVersionCommitMessage);
        expectPushReleaseBranch(testParams);
        expectPushCheckoutBranch();

        run(testParams);
    }

    @Test
    public void releaseBranch_AndTag_Aborted() throws Exception {
        TestParams testParams = new TestParams();
        testParams.releaseBranchName = "release-v2.1";
        testParams.tagName = "v2.1";
        testParams.releaseVersionModified = true;
        testParams.devVersionModified = true;
        testParams.result = Result.ABORTED;
        testParams.setupBasicExpectations();

        expectCheckoutReleaseBranch(testParams);
        expectCommitWorkingCopy(tagCommitMessage);
        expectCreateTag(testParams, tagCommitMessage);
        expectCheckoutCheckoutBranch();
        expectCommitWorkingCopy(devVersionCommitMessage);
        expectFailureRecovery(testParams, DELETE_RELEASE_BRANCH.YES, DELETE_TAG.YES);

        run(testParams);
    }

    @Test
    public void releaseBranch_NoTag_Success() throws Exception {
        TestParams testParams = new TestParams();
        testParams.releaseBranchName = "release-v2.1";
        testParams.releaseVersionModified = true;
        testParams.devVersionModified = true;
        testParams.result = Result.SUCCESS;
        testParams.setupBasicExpectations();

        expectCheckoutReleaseBranch(testParams);
        expectCommitWorkingCopy(tagCommitMessage);
        expectCheckoutCheckoutBranch();
        expectCommitWorkingCopy(devVersionCommitMessage);
        expectPushReleaseBranch(testParams);
        expectPushCheckoutBranch();

        run(testParams);
    }

    @Test
    public void releaseBranch_NoTag_Failure() throws Exception {
        TestParams testParams = new TestParams();
        testParams.releaseBranchName = "release-v2.1";
        testParams.releaseVersionModified = true;
        testParams.devVersionModified = true;
        testParams.result = Result.FAILURE;
        testParams.setupBasicExpectations();

        expectCheckoutReleaseBranch(testParams);
        expectCommitWorkingCopy(tagCommitMessage);
        expectCheckoutCheckoutBranch();
        expectCommitWorkingCopy(devVersionCommitMessage);
        expectFailureRecovery(testParams, DELETE_RELEASE_BRANCH.YES, DELETE_TAG.NO);

        run(testParams);
    }

    @Test
    public void noReleaseBranch_Tag_Success() throws Exception {
        TestParams testParams = new TestParams();
        testParams.tagName = "v2.1";
        testParams.releaseVersionModified = true;
        testParams.devVersionModified = true;
        testParams.result = Result.SUCCESS;
        testParams.setupBasicExpectations();

        expectCheckoutCheckoutBranch();
        expectCommitWorkingCopy(tagCommitMessage);
        expectCreateTag(testParams, tagCommitMessage);
        expectCommitWorkingCopy(devVersionCommitMessage);
        expectPushCheckoutBranch();

        run(testParams);
    }

    @Test
    public void noReleaseBranch_Tag_Aborted() throws Exception {
        TestParams testParams = new TestParams();
        testParams.tagName = "v2.1";
        testParams.releaseVersionModified = true;
        testParams.devVersionModified = true;
        testParams.result = Result.ABORTED;
        testParams.setupBasicExpectations();

        expectCheckoutCheckoutBranch();
        expectCommitWorkingCopy(tagCommitMessage);
        expectCreateTag(testParams, tagCommitMessage);
        expectCommitWorkingCopy(devVersionCommitMessage);
        expectFailureRecovery(testParams, DELETE_RELEASE_BRANCH.NO, DELETE_TAG.YES);

        run(testParams);
    }

    @Test
    public void noReleaseBranch_NoTag_Success() throws Exception {
        TestParams testParams = new TestParams();
        testParams.releaseVersionModified = true;
        testParams.devVersionModified = true;
        testParams.result = Result.SUCCESS;
        testParams.setupBasicExpectations();

        expectCheckoutCheckoutBranch();
        expectCommitWorkingCopy(tagCommitMessage);
        expectCommitWorkingCopy(devVersionCommitMessage);
        expectPushCheckoutBranch();

        run(testParams);
    }

    @Test
    public void noReleaseBranch_NoTag_Failure() throws Exception {
        TestParams testParams = new TestParams();
        testParams.releaseVersionModified = true;
        testParams.devVersionModified = true;
        testParams.result = Result.FAILURE;
        testParams.setupBasicExpectations();

        expectCheckoutCheckoutBranch();
        expectCommitWorkingCopy(tagCommitMessage);
        expectCommitWorkingCopy(devVersionCommitMessage);
        // recover from failure
        expectFailureRecovery(testParams, DELETE_RELEASE_BRANCH.NO, DELETE_TAG.NO);

        run(testParams);
    }

    @Test
    public void noReleaseBranch_NoTag_onlyReleaseVersionChange_Success() throws Exception {
        TestParams testParams = new TestParams();
        testParams.result = Result.SUCCESS;
        testParams.releaseVersionModified = true;
        testParams.devVersionModified = false;
        testParams.setupBasicExpectations();

        expectCheckoutCheckoutBranch();
        expectCommitWorkingCopy(tagCommitMessage);
        expectPushCheckoutBranch();

        run(testParams);
    }

    @Test
    public void noReleaseBranch_NoTag_onlyDevVersionChange_Success() throws Exception {
        TestParams testParams = new TestParams();
        testParams.result = Result.SUCCESS;
        testParams.releaseVersionModified = false;
        testParams.devVersionModified = true;
        testParams.setupBasicExpectations();

        expectCheckoutCheckoutBranch();
        expectCommitWorkingCopy(devVersionCommitMessage);
        expectPushCheckoutBranch();

        run(testParams);
    }

    @Test
    public void noReleaseBranch_NoTag_noVersionChange_Success() throws Exception {
        TestParams testParams = new TestParams();
        testParams.result = Result.SUCCESS;
        testParams.releaseVersionModified = false;
        testParams.devVersionModified = false;
        testParams.setupBasicExpectations();

        expectCheckoutCheckoutBranch();

        run(testParams);
    }

    private enum DELETE_RELEASE_BRANCH {
        YES,
        NO
    }

    private enum DELETE_TAG {
        YES,
        NO
    }
}
