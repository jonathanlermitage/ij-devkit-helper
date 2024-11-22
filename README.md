# DevKit Helper

## A plugin for IntelliJ-based IDE plugin developers

<p>
    Reports usages of IntelliJ Platform API introduced in a version <em>newer</em> than the one specified in <code>pluginSinceBuild</code>
    in <code>gradle.properties</code>.
</p>
<p>
    Using such API may lead to incompatibilities of the plugin with older IDE versions.
</p>
<p>
    To avoid possible issues when running the plugin in older IDE versions, increase <code>pluginSinceBuild</code>
    accordingly,
    or remove usages of this API.
</p>
<p>
    See <a href="https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html">Build Number Ranges</a> in
    IntelliJ Platform Plugin SDK docs for more details.
</p>
<p>
    📢 This inspection is a workaround for the original inspection <code>Usage of IntelliJ API not available in older
    IDEs</code>,
    which reads values from <code>plugin.xml</code>. The IntelliJ Platform Gradle Plugin v2 encourages plugin
    developers to define values in <code>gradle.properties</code> instead. This new inspection has been developed to
    enhance the support of the IntelliJ Platform Gradle Plugin v2 by providing a new inspection <code>Usage of IntelliJ API not available in older IDEs (gradle.properties)</code>.
</p>

Check also my other <a href="https://plugins.jetbrains.com/author/ed9cc7eb-74f5-46c1-b0df-67162fe1a1c5">plugins</a>.
