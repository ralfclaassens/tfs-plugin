package hudson.plugins.tfs;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.Assert;
import org.junit.Test;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.mockito.Mock;

import hudson.model.AbstractProject;
import hudson.model.Job;
import hudson.plugins.tfs.model.TeamBuildPayload;
import hudson.plugins.tfs.util.TeamBuildJobFinder;
import jenkins.model.Jenkins;

/**
 * A class to test {@link TeamBuildEndpoint}.
 */
public class TeamBuildJobFinderTest {
	private @Mock Jenkins jenkins = mock(Jenkins.class);
	private @Mock StaplerRequest req = mock(StaplerRequest.class);
	private @Mock StaplerResponse rsp = mock(StaplerResponse.class);
	private @Mock WorkflowMultiBranchProject wmbp = mock(WorkflowMultiBranchProject.class); 
	private @Mock Job job = mock(Job.class);

	@Test public void findProject() throws Exception {
    	TeamBuildJobFinder jobFinder = new TeamBuildJobFinder(jenkins);
    	when(req.getParameter("json")).thenReturn("{ \"team-build\": { \"Build.SourceBranch\" : \"/refs/head/master\" }}");
        when(jenkins.getItemByFullName("testjob", AbstractProject.class)).thenReturn(null);
        when(wmbp.getJob("master")).thenReturn(job);
        when(jenkins.getItemByFullName("testjob")).thenReturn(wmbp);
        
        Job result = jobFinder.findProject(req, rsp, "testjob");
        Assert.assertEquals(result.getName(), "master");
        Assert.assertEquals(jobFinder.getTeamBuildPayload(), new TeamBuildPayload());
    }

}
