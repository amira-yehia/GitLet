package gitlet;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

import static gitlet.Utils.join;

public class Repository implements Serializable {
    /** The current working directory. */
    public static final File CWD = new File(System.getProperty("user.dir"));
    /** The .gitlet directory. */
    public static final File GITLET_DIR = join(CWD, "/.gitlet");
    public  static  final File COMMITS_DIR = join(CWD, "/.gitlet/commits");
    public  static  final File STAGING_DIR = join(CWD, "/.gitlet/staging");

    private String head;

    /** Staging Area, maps the name of the file, useful for figuring out
     * whether we need to swap it out for existing file in commit, or add
     * it entirely new. */
    private HashMap<String, String> stagingArea;

    /** Untracked files are like the opposite of the Staging Area,
     * these are files that WERE tracked before, and now, for the
     * next commit, they're not going to be added. */
    private ArrayList<String> untrackedFiles;

    /** Overseer of entire tree structure, each branch has a name (String)
     * and a hash ID of its current position so that we can find the commit
     * that it's pointing to.*/
    private HashMap<String, String> branches;

    /** Returns _stagingArea. */
    public HashMap<String, String> getStagingArea() {
        return this.stagingArea;
    }

    /** Returns untrackedFiles. */
    public ArrayList<String> getUntrackedFiles() {
        return this.untrackedFiles;
    }

    /** Returns branches. */
    public HashMap<String, String> getBranches() {
        return this.branches;
    }

    /** Returns the uid of the current head which
     * corresponds to the head branch. */
    public String getHead() {
        return this.branches.get(this.head);
    }

    // check if there are uncommited changes
    public boolean hasUncommittedChanges() {
        // Logic to check for uncommitted changes in the repository
        return !this.stagingArea.isEmpty() || !this.untrackedFiles.isEmpty();
    }

    /*********************** INIT ****************/
    public Repository() {
        Commit initial = Commit.initialCommit() ;
        // Create .gitlet directory
        GITLET_DIR.mkdir() ;
        COMMITS_DIR.mkdir() ;
        STAGING_DIR.mkdir() ;

        String id = initial.getUniversalID();
        File initialFile = join(COMMITS_DIR , id);
        Utils.writeContents(initialFile , Utils.serialize(initial)) ;
        this.head = "master" ;
        this.branches = new HashMap<String , String>();
        this.branches.put("master", id);

        this.stagingArea = new HashMap<String , String>();
        this.untrackedFiles = new ArrayList<String>() ;
    }

    /*********************** ADD FILES ****************/
    public void add(String fileName) {
        File file = new File(fileName);
        if(!file.exists()){
            System.out.println("File does not exist");
            return;
        }

        String fileHash = Utils.sha1(fileName  + Utils.readContentsAsString(file)) ;
        Commit lastCommit = this.uidToCommit(getHead()) ;
        HashMap<String , String> trackedFiles = lastCommit.getFiles() ;

        File stagingBlob = join(STAGING_DIR, fileHash);
        boolean isTrackedFiles = (trackedFiles != null);

        if(!isTrackedFiles || !trackedFiles.containsKey(fileName) || !trackedFiles.get(fileName).equals(fileHash)){
            this.stagingArea.put(fileName, fileHash);
            String contents = Utils.readContentsAsString(file) ;
            Utils.writeContents(stagingBlob , contents) ;
        }
        else{
            if(stagingBlob.exists()){
                this.stagingArea.remove(fileName);
            }
        }

        if(this.untrackedFiles.contains(fileName)){
            this.untrackedFiles.remove(fileName);
        }

        System.out.println("Added file to staging: " + fileName);
    }

    /*********************** COMMIT ****************/
    public void commit(String msg){
        if(msg.trim().equals("")){
            Utils.message("Please enter a commit message.");
            throw new GitletException();
        }

        Commit lastCommit = this.uidToCommit(getHead()) ;
        HashMap<String , String> trackedFiles = lastCommit.getFiles() ;

        if(trackedFiles == null){
            trackedFiles = new HashMap<String , String>() ;
        }

        if(this.stagingArea.size() != 0 || this.untrackedFiles.size() != 0){
            for (String fileName : this.stagingArea.keySet()) {
                trackedFiles.put(fileName, this.stagingArea.get(fileName));
            }

            for (String fileName : this.untrackedFiles) {
                trackedFiles.remove(fileName);
            }
        }
        else{
            Utils.message("No changes added to the commit.");
            throw new GitletException();
        }

        String[] parent = new String[]{lastCommit.getUniversalID()} ;
        Commit currentCommit = new Commit(msg, trackedFiles, parent, true);
        String id = currentCommit.getUniversalID();
        File newCommFile = join(COMMITS_DIR , id);
        Utils.writeObject(newCommFile, currentCommit);

        this.stagingArea = new HashMap<String, String>();
        this.untrackedFiles = new ArrayList<String>();
        this.branches.put(this.head, id);
    }

    public Commit uidToCommit(String uid) {
        File f = new File(COMMITS_DIR + "/"+uid);
        if (f.exists()) {
            return Utils.readObject(f, Commit.class);
        } else {
            Utils.message("No commit with that id exists.");
            throw new GitletException();
        }
    }

    /*********************** REMOVE ****************/
    public void rm(String fileName) {
        File file = new File(fileName);
        Commit lastCommit = this.uidToCommit(getHead());
        HashMap<String, String> trackedFiles = lastCommit.getFiles();

        if (!file.exists() && (trackedFiles == null || !trackedFiles.containsKey(fileName))) {
            Utils.message("File does not exist.");
            throw new GitletException();
        }

        boolean changed = false;

        // Remove from staging area
        if (this.stagingArea.containsKey(fileName)) {
            String fileHash = this.stagingArea.get(fileName);
            File stagedFile = join(STAGING_DIR, fileHash);
            
            if (stagedFile.exists()) {
                stagedFile.delete(); // Delete the file from STAGING_DIR
            }
            this.stagingArea.remove(fileName); // Remove from stagingArea map
            changed = true;
        }

        // Mark for untracking if it exists in tracked files
        if (trackedFiles != null && trackedFiles.containsKey(fileName)) {
            this.untrackedFiles.add(fileName); // Add to untrackedFiles list
            File fileToRemove = new File(fileName);
            Utils.restrictedDelete(fileToRemove); // Delete the file from the working directory
            changed = true;
        }

        if (!changed) {
            Utils.message("No reason to remove the file.");
            throw new GitletException();
        }
    }

    /*********************** LOG ****************/
    public void log(){
        String tmpHead = getHead();

        while(tmpHead != null){
            Commit currentCommit = uidToCommit(tmpHead) ;
            printCommit(tmpHead);
            tmpHead = currentCommit.getParentID();
        }
    }

    public  void printCommit(String uid){
        Commit currentCommit = uidToCommit(uid) ;
        System.out.println("===");
        System.out.println("commit " + uid);
        System.out.println("Date: " + currentCommit.getTimestamp());
        System.out.println(currentCommit.getMessage());
        System.out.println();
    }

    /*********************** GLOBAL LOG ****************/
    public void globalLog() {
        File commitFolder = new File(".gitlet/commits");
        File[] commits = commitFolder.listFiles();

        for (File file : commits) {
            printCommit(file.getName());
        }
    }


    /*********************** CHECKOUT ****************/
    // checkout 4-functions usage
    public void checkout(String[] parameters) {
        // Check for uncommitted changes before proceeding
        if (hasUncommittedChanges()) {
            Utils.message("You have uncommitted changes. Commit or discard them before switching branches.");
            throw new GitletException(); // Prevent checkout with uncommitted changes
        }
        else if (parameters.length == 2 && parameters[0].equals("restore")) {
            // Case 1: Restore a file to its version in the current commit
            String fileName = parameters[1];
            restoreFileToCurrentCommit(fileName);
        } else if (parameters.length == 3 && parameters[1].equals("restore")) {
            // Case 2: Restore a file to its version in a specific commit
            String commitId = parameters[0];
            String fileName = parameters[2];
            restoreFileToSpecificCommit(commitId, fileName);
        } else if (parameters.length == 2 && parameters[0].equals("new")) {
            // Case 3: Switch to another branch
            String requiredID = parameters[1];
            // Ensure the branch name is valid
            if (requiredID == null || requiredID.trim().isEmpty()) {
                Utils.message("Please specify a valid ID.");
                throw new GitletException();
            }
            else {
                checkoutCommit(requiredID);
            }
        }
        /*else if (parameters.length == 2 && parameters[0].equals("branch")) {
            // Case 3: Switch to another branch
            String branchName = parameters[1];
            // Ensure the branch name is valid
            if (branchName == null || branchName.trim().isEmpty()) {
                Utils.message("Please specify a branch name.");
                throw new GitletException();
            }
            else {
                switchBranch(branchName);
            }
        } */
        else {
            Utils.message("Incorrect Arguments for Checkout.");
            throw new GitletException();
        }
    }
    // for restore a file in the current commmit if we make a change without making commit to it
    public void restoreFileToCurrentCommit(String fileName) {
        Commit currentCommit = uidToCommit(getHead());
        HashMap<String, String> trackedFiles = currentCommit.getFiles();

        if (trackedFiles == null || !trackedFiles.containsKey(fileName)) {
            Utils.message("File doesn't exist in that commit.");
            throw new GitletException();
        }

        String fileHash = trackedFiles.get(fileName);
        File blobFile = join(STAGING_DIR, fileHash);

        if (!blobFile.exists()) {
            Utils.message("File contents are missing from the repository.");
            throw new GitletException();
        }

        String contents = Utils.readContentsAsString(blobFile);
        Utils.writeContents(new File(fileName), contents);
    }

    // for restore a file in the another commmit if we make a change without making commit to it
    public void restoreFileToSpecificCommit(String commitId, String fileName) {
        Commit commit = uidToCommit(commitId);
        HashMap<String, String> trackedFiles = commit.getFiles();

        if (trackedFiles == null || !trackedFiles.containsKey(fileName)) {
            Utils.message("File doesn't exist in that commit.");
            throw new GitletException();
        }

        String fileHash = trackedFiles.get(fileName);
        File blobFile = join(STAGING_DIR, fileHash);

        if (!blobFile.exists()) {
            Utils.message("File contents are missing from the repository.");
            throw new GitletException();
        }

        String contents = Utils.readContentsAsString(blobFile);
        Utils.writeContents(new File(fileName), contents);
    }

    // switching from branch to another
    /*public void switchBranch(String branchName) {
        if (!branches.containsKey(branchName)) {
            Utils.message("No such branch exists.");
            throw new GitletException();
        }

        if (branchName.equals(head)) {
            Utils.message("No need to checkout the current branch.");
            throw new GitletException();
        }

        head = branchName;
        String commitId = branches.get(head);
        Commit commit = uidToCommit(commitId);

        // Clear working directory and reset to the branch's commit
        HashMap<String, String> trackedFiles = commit.getFiles();
        if (trackedFiles != null) {
            for (String fileName : trackedFiles.keySet()) {
                String fileHash = trackedFiles.get(fileName);
                File blobFile = join(STAGING_DIR, fileHash);
                String contents = Utils.readContentsAsString(blobFile);
                Utils.writeContents(new File(fileName), contents);
            }
        }

        stagingArea.clear();
        untrackedFiles.clear();
    }*/

    // for moving from the current commit to another
    public void checkoutCommit(String commitID) {
        // find the commit using its ID
        Commit targetCommit = uidToCommit(commitID);
        if (targetCommit == null) {
            Utils.message("No commit with that id exists.");
            throw new GitletException();
        }

        // update the working directory with files from the target commit
        HashMap<String, String> targetFiles = targetCommit.getFiles();
        for (File file : CWD.listFiles()) {
            if (!file.isDirectory()) {
                Utils.restrictedDelete(file); // for removing all files existing to move into the new commit
            }
        }

        if (targetFiles != null) {
            for (String fileName : targetFiles.keySet()) {
                String fileHash = targetFiles.get(fileName);
                File blobFile = join(STAGING_DIR, fileHash);
                String fileContent = Utils.readContentsAsString(blobFile);
                File restoredFile = join(CWD, fileName);
                Utils.writeContents(restoredFile, fileContent);
            }
        }
        // clearing staging area and updating head
        this.stagingArea.clear();
        this.branches.put(this.head, commitID);

        System.out.println("CheckeOut into: " + commitID);
    }
    // find all ids of a commit message
    public void find(String commitName){
        String tmpHead = getHead();
        boolean ch = true;
        while(tmpHead != null){
            Commit currentCommit = uidToCommit(tmpHead);
            String messageName = currentCommit.getMessage();
            StringBuilder sb = new StringBuilder(messageName);
            sb.deleteCharAt(messageName.length() - 1);
            if(sb.toString().equals(commitName)){
                System.out.println(tmpHead);
                ch = false;
            }
            // System.out.println("." + currentCommit.getMessage() + ".");
            // System.out.println(commitName);
            tmpHead = currentCommit.getParentID();
        }
        if(ch){
            System.out.println("Found no commit with that message.");
        }
    }
    // get status of gitlet
    public void status() {
        System.out.println("=== Branches ===");
        for (String branchName : this.branches.keySet()) {
            if (branchName.equals(this.head)) {
                System.out.println("*" + branchName);
            } else {
                System.out.println(branchName);
            }
        }
        System.out.println();
    
        System.out.println("=== Staged Files ===");
        for (String stagedFile : this.stagingArea.keySet()) {
            System.out.println(stagedFile);
        }
        System.out.println();
    
        System.out.println("=== Removed Files ===");
        for (String removedFile : this.untrackedFiles) {
            System.out.println(removedFile);
        }
        System.out.println();
    
        System.out.println("=== Modifications Not Staged For Commit ===");
        ArrayList<String> modifiedFiles = getModifications();
        for (String modifiedFile : modifiedFiles) {
            System.out.println(modifiedFile);
        }
        System.out.println();
    
        System.out.println("=== Untracked Files ===");
        ArrayList<String> untracked = getUntrackedFilesCWD();
        for (String untrackedFile : untracked) {
            System.out.println(untrackedFile);
        }
        System.out.println();
    }
    
    public ArrayList<String> getUntrackedFilesCWD() {
        ArrayList<String> untracked = new ArrayList<>();
        File[] cwdFiles = CWD.listFiles();
    
        for (File file : cwdFiles) {
            String fileName = file.getName();
            boolean isTracked = false;
    
            // Check if file is tracked
            Commit lastCommit = uidToCommit(getHead());
            if (lastCommit.getFiles() != null && lastCommit.getFiles().containsKey(fileName)) {
                isTracked = true;
            }
    
            // Check if file is staged
            if (this.stagingArea.containsKey(fileName)) {
                isTracked = true;
            }
    
            // If not tracked or staged, add to untracked
            if (!isTracked && !file.isDirectory()) {
                untracked.add(fileName);
            }
        }
        
        return untracked;
    }
    
    public ArrayList<String> getModifications() {
        ArrayList<String> modifications = new ArrayList<>();
        // check modifications in staging area
        for (String fileName : this.stagingArea.keySet()) {
            File file = new File(fileName);
            String stagedHash = this.stagingArea.get(fileName);
    
            if (!file.exists()) {
                modifications.add(fileName + " (deleted)");
            } else {
                String currentHash = Utils.sha1(Utils.readContents(file));
                StringBuilder sh = new StringBuilder(stagedHash);
                StringBuilder ch = new StringBuilder(currentHash);
                if (!sh.toString().equals(ch.toString())) {
                    modifications.add(fileName + " (modified)");
                }
            }
        }
        // check modifications in the tracked files
        Commit lastCommit = uidToCommit(getHead());
        if (lastCommit.getFiles() != null) {
            for (String fileName : lastCommit.getFiles().keySet()) {
                File file = new File(fileName);
                if (!file.exists()) {
                    modifications.add(fileName + " (deleted)");
                } else {
                    String currentHash = Utils.sha1(Utils.readContents(file));
                    String lastCommitHash = lastCommit.getFiles().get(fileName);
                    StringBuilder lh = new StringBuilder(lastCommitHash);
                    StringBuilder ch = new StringBuilder(currentHash);
                    if (!lh.toString().equals(ch.toString()) && !this.stagingArea.containsKey(fileName)) {
                        modifications.add(fileName + " (modified)");
                    }
                }
            }
        }
        
        return modifications;
    }    

}