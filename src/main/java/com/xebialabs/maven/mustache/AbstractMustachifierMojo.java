package com.xebialabs.maven.mustache;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.List;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.shared.model.fileset.FileSet;
import com.google.common.io.Closeables;

import com.xebialabs.maven.mustache.utils.TFiles;

import de.schlichtherle.truezip.file.TFile;
import de.schlichtherle.truezip.fs.FsSyncException;

/**
 * Based class for the MustachifierMojos
 */
public abstract class AbstractMustachifierMojo extends AbstractMojo {
    /**
     * FileSet including special entries
     *
     * @parameter
     */
    protected List<FileSet> filesets;
    /**
     * The delimiter format
     *
     * @parameter default-value="{{ }}"
     */
    protected String delimiters;
    /**
     * The separator format from the placeholder name and the default value
     *
     * @parameter default-value=":"
     */
    protected String valueSeparator;


    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        if (!getDelimiters().contains(" "))
            throw new MojoExecutionException("Invalid delimiter format: it should include one space character");

        ArchiveFileSetManager fileSetManager = new ArchiveFileSetManager();

        for (FileSet fileset : filesets) {
            final String[] includedFiles = fileSetManager.getIncludedFiles(fileset);

            if (includedFiles.length > 0)
                updateManifestFile(fileset.getDirectory());

            for (String includedFile : includedFiles) {
                TFile file = new TFile(fileset.getDirectory(), includedFile);
                getLog().debug(" processing " + file);

                final String content = load(file, fileset.getModelEncoding());
                if (getLog().isDebugEnabled())
                    getLog().debug(" before processing : " + content);

                String processed = transform(content);
                if (getLog().isDebugEnabled())
                    getLog().debug(" after processing : " + content);
                save(processed, file, fileset.getModelEncoding());
            }

            flush(fileset.getDirectory());
        }
    }

    protected abstract String transform(final String content);

    public List<FileSet> getFilesets() {
        return filesets;
    }

    public void setFilesets(final List<FileSet> filesets) {
        this.filesets = filesets;
    }

    public String getDelimiters() {
        return delimiters;
    }

    public void setDelimiters(final String delimiters) {
        this.delimiters = delimiters;
    }

    public String getValueSeparator() {
        return valueSeparator;
    }

    public void setValueSeparator(final String valueSeparator) {
        this.valueSeparator = valueSeparator;
    }

    protected String getAfterDelimiter() {
        return delimiters.split(" ")[1];
    }

    protected String getBeforeDelimiter() {
        return delimiters.split(" ")[0];
    }

    protected void save(final String content, final TFile file, String encoding) throws MojoExecutionException {
        Writer out = null;
        try {
            TFiles.write(content, file, Charset.forName(encoding));
        } catch (Exception e) {
            throw new MojoExecutionException("Cannot write into input file [" + file + "]", e);
        } finally {
            Closeables.closeQuietly(out);
        }
    }

    protected String load(final TFile file, String encoding) throws MojoExecutionException {
        try {
            return TFiles.toString(file, Charset.forName(encoding));
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot read the input file [" + file + "]", e);
        }
    }

    protected void flush(final String directory) {
        TFile archive = new TFile(directory);
        if (!archive.isArchive())
            return;

        try {
            TFiles.flush(archive);
        } catch (FsSyncException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

    }

    protected void updateManifestFile(final String directory) throws MojoExecutionException {
        TFile archive = new TFile(directory);
        if (!archive.isArchive()) {
            return;
        }

        getLog().debug("Update the manifest file " + directory);
        TFile manifest = new TFile(archive, "META-INF/MANIFEST.MF");
        if (!manifest.exists()) {
            return;
        }

        try {
            final byte[] bytes;
            bytes = TFiles.toByteArray(manifest);
            TFiles.fromByteArray(manifest, bytes);
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot update manifest file", e);
        }
    }
}
