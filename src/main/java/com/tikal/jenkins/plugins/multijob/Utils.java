package com.tikal.jenkins.plugins.multijob;

import com.tikal.jenkins.plugins.multijob.views.TableProperty;
import hudson.model.User;
import hudson.plugins.groovy.FileScriptSource;
import hudson.plugins.groovy.ScriptSource;
import hudson.plugins.groovy.StringScriptSource;
import jenkins.model.Jenkins;

import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.Properties;

public final class Utils {

    private Utils() {
    }

    public static @Nonnull Properties parseProperties(final String properties) throws IOException {
        Properties props = new Properties();

        if (null != properties) {
            try {
                props.load(new StringReader(properties));
            } catch (NoSuchMethodError e) {
                props.load(new ByteArrayInputStream(properties.getBytes()));
            }
        }
        return props;
    }

    public static TableProperty getTableProperty() throws IOException {
        User user = Jenkins.getInstance().getUser(Jenkins.getAuthentication().getName());
        TableProperty property = user.getProperty(TableProperty.class);
        if (null == property) {
            property = TableProperty.DESCRIPTOR.newInstance(user);
            user.addProperty(property);
        }
        return property;
    }

    public static ScriptSource getScriptSource(boolean isUseFile, String scriptText, String scriptPath) {
        if (isUseFile) {
            return new FileScriptSource(scriptPath);
        }
        return new StringScriptSource(scriptText);
    }
}
