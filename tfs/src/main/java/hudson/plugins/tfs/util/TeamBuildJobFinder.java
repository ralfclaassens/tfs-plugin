package hudson.plugins.tfs.util;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

import hudson.model.AbstractProject;
import hudson.model.BuildAuthorizationToken;
import hudson.model.Job;
import hudson.plugins.git.GitStatus;
import hudson.plugins.tfs.model.TeamBuildPayload;
import jenkins.model.Jenkins;
import jenkins.plugins.git.GitSCMSource;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.SCMSourceOwners;
import net.sf.json.JSONObject;

/**
 * Class which helps {@link hudson.plugins.tfs.util.TeamBuildEndpoint} in order to handle 
 * build triggers from TFS for so-called MultiBranchProject job types in Jenkins.
 * 
 * @author Ralf Claassens
 */
public class TeamBuildJobFinder {
	private static final Logger LOGGER = Logger.getLogger(TeamBuildJobFinder.class.getName());
	
	private TeamBuildPayload teamBuildPayload = null;

	private JSONObject formData = null;
	
	private Jenkins jenkins = Jenkins.getInstance();
	
	public TeamBuildJobFinder() { }
	
	public TeamBuildJobFinder(Jenkins jenkins) {
		this.jenkins = jenkins;
	}
	
	public Job findProject(StaplerRequest req, StaplerResponse rsp, String jobName) 
			throws IOException, ServletException {
        Job project = jenkins.getItemByFullName(jobName, AbstractProject.class);
		
        final ObjectMapper mapper = EndpointHelper.MAPPER;

        if (project == null) {
            String parent = jobName;
            String branchName = "master";
            WorkflowMultiBranchProject wmbp = (WorkflowMultiBranchProject) jenkins.getItemByFullName(parent);

            if (jenkins.getItemByFullName(parent) == null || wmbp instanceof WorkflowMultiBranchProject == false) {
                throw new IllegalArgumentException("Project not found");
            }

            formData = JSONObject.fromObject(req.getParameter("json"));
            teamBuildPayload = mapper.convertValue(formData, TeamBuildPayload.class);

            String repoUrl = teamBuildPayload.BuildVariables.get("Build.Repository.Uri");
            String buildSourceBranch = teamBuildPayload.BuildVariables.get("Build.SourceBranch");

            branchName = findBranchName(wmbp, buildSourceBranch);
            project = wmbp.getJob(branchName);

            if (project == null) {
                // If branch does not exist, execute branch indexing below.
                startBranchIndexing(repoUrl);

                try {
                    // Wait for branch indexing to avoid triggering builds for
                    // jobs/branches that do not exist yet in Jenkins.
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    LOGGER.log(Level.SEVERE, "InterruptedException", e);
                }

                // After branch indexing, try to find branch name one more time.
                branchName = findBranchName(wmbp, buildSourceBranch);
                project = wmbp.getJob(branchName);
            }
        } else {
        	checkPermission((AbstractProject) project, req, rsp);
            formData = req.getSubmittedForm();
            teamBuildPayload = mapper.convertValue(formData, TeamBuildPayload.class);
        }
        
        return project;
	}

    /**
     * Searches for a branch name in a WorkflowMultiBranchProject based on
     * Build.SourceBranch from the BuildVariables of TeamBuildPayload.
     * 
     * @param wmbp
     *            WorkflowMultiBranchProject where the branch name should be
     *            searched for
     * @param buildSourceBranch
     *            Build.SourceBranch string retrieved from the BuildVariables of
     *            payload
     * @return the branch name found
     * @throws UnsupportedEncodingException
     *             If it is not possible to URLEncode the branch name
     */
    private String findBranchName(WorkflowMultiBranchProject wmbp, String buildSourceBranch)
            throws UnsupportedEncodingException {
        String branchName = buildSourceBranch.replace("refs/heads/", "");
        
        if (wmbp.getJob(branchName) == null) {
            branchName = URLEncoder.encode(branchName, "UTF-8");
        }
        
        return branchName;
    }

    /**
     * Triggers a branch indexing for the jobs containing the same repository
     * URL as the repoUrl parameter.
     * 
     * @param repoUrl
     *            repository URL used for searching for jobs
     */
    private void startBranchIndexing(String repoUrl) {
        for (final SCMSourceOwner owner : SCMSourceOwners.all()) {
            for (SCMSource source : owner.getSCMSources()) {
                if (source instanceof GitSCMSource) {
                    GitSCMSource git = (GitSCMSource) source;
                    try {
                        URIish remote = new URIish(git.getRemote());
                        URIish uri = new URIish(repoUrl);
                        if (GitStatus.looselyMatches(uri, remote)) {
                            LOGGER.info("Triggering the indexing of " + owner.getFullDisplayName());
                            owner.onSCMSourceUpdated(source);
                        }
                    } catch (URISyntaxException e) {
                        continue;
                    }
                }
            }
        }
    }

    @SuppressWarnings("deprecation" /* We want to do exactly what Jenkins does */)
    void checkPermission(final AbstractProject project, final StaplerRequest req, final StaplerResponse rsp) throws IOException {
        Job<?, ?> job = project;
        final BuildAuthorizationToken authToken = project.getAuthToken();
        hudson.model.BuildAuthorizationToken.checkPermission(job, authToken, req, rsp);
    }
    
	public TeamBuildPayload getTeamBuildPayload() {
		return teamBuildPayload;
	}

	public JSONObject getFormData() {
		return formData;
	}
	
}
