/*
 * Copyright (C) 2014 The Dagger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dagger.internal.codegen;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;
import static dagger.internal.codegen.GeneratedLines.GENERATED_ANNOTATION;
import static dagger.internal.codegen.GeneratedLines.NPE_FROM_PROVIDES_METHOD;

import com.google.common.collect.ImmutableList;
import com.google.testing.compile.JavaFileObjects;
import java.util.Collection;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ProductionComponentProcessorTest {
  @Parameters(name = "{0}")
  public static Collection<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  private final CompilerMode compilerMode;

  public ProductionComponentProcessorTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test public void componentOnConcreteClass() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.NotAComponent",
        "package test;",
        "",
        "import dagger.producers.ProductionComponent;",
        "",
        "@ProductionComponent",
        "final class NotAComponent {}");
    assertAbout(javaSource()).that(componentFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("interface");
  }

  @Test public void componentOnEnum() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.NotAComponent",
        "package test;",
        "",
        "import dagger.producers.ProductionComponent;",
        "",
        "@ProductionComponent",
        "enum NotAComponent {",
        "  INSTANCE",
        "}");
    assertAbout(javaSource())
        .that(componentFile)
        .withCompilerOptions(compilerMode.javacopts())
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("interface");
  }

  @Test public void componentOnAnnotation() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.NotAComponent",
        "package test;",
        "",
        "import dagger.producers.ProductionComponent;",
        "",
        "@ProductionComponent",
        "@interface NotAComponent {}");
    assertAbout(javaSource())
        .that(componentFile)
        .withCompilerOptions(compilerMode.javacopts())
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("interface");
  }

  @Test public void nonModuleModule() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.NotAComponent",
        "package test;",
        "",
        "import dagger.producers.ProductionComponent;",
        "",
        "@ProductionComponent(modules = Object.class)",
        "interface NotAComponent {}");
    assertAbout(javaSource())
        .that(componentFile)
        .withCompilerOptions(compilerMode.javacopts())
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("is not annotated with one of @Module, @ProducerModule");
  }

  @Test
  public void dependsOnProductionExecutor() {
    JavaFileObject moduleFile =
        JavaFileObjects.forSourceLines(
            "test.ExecutorModule",
            "package test;",
            "",
            "import com.google.common.util.concurrent.MoreExecutors;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.producers.Production;",
            "import java.util.concurrent.Executor;",
            "",
            "@Module",
            "final class ExecutorModule {",
            "  @Provides @Production Executor executor() {",
            "    return MoreExecutors.directExecutor();",
            "  }",
            "}");
    JavaFileObject producerModuleFile =
        JavaFileObjects.forSourceLines(
            "test.SimpleModule",
            "package test;",
            "",
            "import dagger.producers.ProducerModule;",
            "import dagger.producers.Produces;",
            "import dagger.producers.Production;",
            "import java.util.concurrent.Executor;",
            "",
            "@ProducerModule",
            "final class SimpleModule {",
            "  @Produces String str(@Production Executor executor) {",
            "    return \"\";",
            "  }",
            "}");
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
            "test.SimpleComponent",
            "package test;",
            "",
            "import com.google.common.util.concurrent.ListenableFuture;",
            "import dagger.producers.ProductionComponent;",
            "import java.util.concurrent.Executor;",
            "",
            "@ProductionComponent(modules = {ExecutorModule.class, SimpleModule.class})",
            "interface SimpleComponent {",
            "  ListenableFuture<String> str();",
            "",
            "  @ProductionComponent.Builder",
            "  interface Builder {",
            "    SimpleComponent build();",
            "  }",
            "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(moduleFile, producerModuleFile, componentFile))
        .withCompilerOptions(compilerMode.javacopts())
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("may not depend on the production executor");
  }

  @Test
  public void simpleComponent() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestClass",
            "package test;",
            "",
            "import com.google.common.util.concurrent.ListenableFuture;",
            "import com.google.common.util.concurrent.MoreExecutors;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.producers.ProducerModule;",
            "import dagger.producers.Produces;",
            "import dagger.producers.Production;",
            "import dagger.producers.ProductionComponent;",
            "import java.util.concurrent.Executor;",
            "import javax.inject.Inject;",
            "",
            "final class TestClass {",
            "  static final class C {",
            "    @Inject C() {}",
            "  }",
            "",
            "  interface A {}",
            "  interface B {}",
            "",
            "  @Module",
            "  static final class BModule {",
            "    @Provides B b(C c) {",
            "      return null;",
            "    }",
            "",
            "    @Provides @Production Executor executor() {",
            "      return MoreExecutors.directExecutor();",
            "    }",
            "  }",
            "",
            "  @ProducerModule",
            "  static final class AModule {",
            "    @Produces ListenableFuture<A> a(B b) {",
            "      return null;",
            "    }",
            "  }",
            "",
            "  @ProductionComponent(modules = {AModule.class, BModule.class})",
            "  interface SimpleComponent {",
            "    ListenableFuture<A> a();",
            "  }",
            "}");
    JavaFileObject generatedComponent;
    switch (compilerMode) {
      case EXPERIMENTAL_ANDROID:
        generatedComponent =
            JavaFileObjects.forSourceLines(
                "test.DaggerTestClass_SimpleComponent",
                "package test;",
                "",
                "import com.google.common.util.concurrent.ListenableFuture;",
                "import dagger.internal.DoubleCheck;",
                "import dagger.internal.InstanceFactory;",
                "import dagger.internal.Preconditions;",
                "import dagger.internal.SetFactory;",
                "import dagger.producers.Producer;",
                "import dagger.producers.internal.Producers;",
                "import dagger.producers.monitoring.ProductionComponentMonitor;",
                "import java.util.concurrent.Executor;",
                "import javax.annotation.Generated;",
                "import javax.inject.Provider;",
                "",
                GENERATED_ANNOTATION,
                "public final class DaggerTestClass_SimpleComponent",
                "    implements TestClass.SimpleComponent {",
                "  private TestClass.BModule bModule;",
                "",
                "  private Provider<Executor> executorProvider2;",
                "",
                "  private Provider<TestClass.SimpleComponent> simpleComponentProvider;",
                "",
                "  private Provider<ProductionComponentMonitor> monitorProvider;",
                "",
                "  private Producer<TestClass.B> bProducer;",
                "",
                "  private Producer<TestClass.A> aProducer;",
                "",
                "  private DaggerTestClass_SimpleComponent(Builder builder) {",
                "    initialize(builder);",
                "  }",
                "",
                "  public static Builder builder() {",
                "    return new Builder();",
                "  }",
                "",
                "  public static TestClass.SimpleComponent create() {",
                "    return new Builder().build();",
                "  }",
                "",
                "  private Executor getProductionExecutorInstance() {",
                "    return Preconditions.checkNotNull(",
                "        bModule.executor(), " + NPE_FROM_PROVIDES_METHOD + ");",
                "  }",
                "",
                "  private Provider<Executor> getProductionExecutorProvider() {",
                "    return new Provider<Executor>() {",
                "      @Override",
                "      public Executor get() {",
                "        return getProductionExecutorInstance();",
                "      }",
                "    };",
                "  }",
                "",
                "  private TestClass.C getCInstance() {",
                "    return new TestClass.C();",
                "  }",
                "",
                "  private TestClass.B getBInstance() {",
                "    return Preconditions.checkNotNull(",
                "        bModule.b(getCInstance()), " + NPE_FROM_PROVIDES_METHOD + ");",
                "  }",
                "",
                "  private Provider<TestClass.B> getBProvider() {",
                "    return new Provider<TestClass.B>() {",
                "      @Override",
                "      public TestClass.B get() {",
                "        return getBInstance();",
                "      }",
                "    };",
                "  }",
                "",
                "  private Producer<TestClass.B> getBProducer() {",
                "    return bProducer;",
                "  }",
                "",
                "  @SuppressWarnings(\"unchecked\")",
                "  private void initialize(final Builder builder) {",
                "    this.bModule = builder.bModule;",
                "    this.executorProvider2 =",
                "        DoubleCheck.provider(",
                "            TestClass_SimpleComponent_ProductionExecutorModule_ExecutorFactory",
                "                .create(getProductionExecutorProvider()));",
                "    this.simpleComponentProvider =",
                "        InstanceFactory.<TestClass.SimpleComponent>create(this);",
                "    this.monitorProvider =",
                "        DoubleCheck.provider(",
                "            TestClass_SimpleComponent_MonitoringModule_MonitorFactory.create(",
                "                simpleComponentProvider,",
                "                SetFactory.<ProductionComponentMonitor.Factory>empty()));",
                "    this.bProducer = Producers.producerFromProvider(getBProvider());",
                "    this.aProducer =",
                "        new TestClass_AModule_AFactory(",
                "            builder.aModule, executorProvider2, monitorProvider, getBProducer());",
                "  }",
                "",
                "  @Override",
                "  public ListenableFuture<TestClass.A> a() {",
                "    return aProducer.get();",
                "  }",
                "",
                "  public static final class Builder {",
                "    private TestClass.BModule bModule;",
                "",
                "    private TestClass.AModule aModule;",
                "",
                "    private Builder() {}",
                "",
                "    public TestClass.SimpleComponent build() {",
                "      if (bModule == null) {",
                "        this.bModule = new TestClass.BModule();",
                "      }",
                "      if (aModule == null) {",
                "        this.aModule = new TestClass.AModule();",
                "      }",
                "      return new DaggerTestClass_SimpleComponent(this);",
                "    }",
                "",
                "    public Builder aModule(TestClass.AModule aModule) {",
                "      this.aModule = Preconditions.checkNotNull(aModule);",
                "      return this;",
                "    }",
                "",
                "    public Builder bModule(TestClass.BModule bModule) {",
                "      this.bModule = Preconditions.checkNotNull(bModule);",
                "      return this;",
                "    }",
                "",
                "    @Deprecated",
                "    public Builder testClass_SimpleComponent_ProductionExecutorModule(",
                "        TestClass_SimpleComponent_ProductionExecutorModule",
                "            testClass_SimpleComponent_ProductionExecutorModule) {",
                "      Preconditions.checkNotNull(",
                "          testClass_SimpleComponent_ProductionExecutorModule);",
                "      return this;",
                "    }",
                "  }",
                "}");
        break;
      default:
        generatedComponent =
            JavaFileObjects.forSourceLines(
                "test.DaggerTestClass_SimpleComponent",
                "package test;",
                "",
                "import com.google.common.util.concurrent.ListenableFuture;",
                "import dagger.internal.DoubleCheck;",
                "import dagger.internal.InstanceFactory;",
                "import dagger.internal.Preconditions;",
                "import dagger.internal.SetFactory;",
                "import dagger.producers.Producer;",
                "import dagger.producers.internal.Producers;",
                "import dagger.producers.monitoring.ProductionComponentMonitor;",
                "import java.util.concurrent.Executor;",
                "import javax.annotation.Generated;",
                "import javax.inject.Provider;",
                "",
                GENERATED_ANNOTATION,
                "public final class DaggerTestClass_SimpleComponent",
                "    implements TestClass.SimpleComponent {",
                "  private Provider<Executor> executorProvider;",
                "  private Provider<Executor> executorProvider2;",
                "  private Provider<TestClass.SimpleComponent> simpleComponentProvider;",
                "  private Provider<ProductionComponentMonitor> monitorProvider;",
                "  private Provider<TestClass.B> bProvider;",
                "  private Producer<TestClass.B> bProducer;",
                "  private Producer<TestClass.A> aProducer;",
                "",
                "  private DaggerTestClass_SimpleComponent(Builder builder) {",
                "    initialize(builder);",
                "  }",
                "",
                "  public static Builder builder() {",
                "    return new Builder();",
                "  }",
                "",
                "  public static TestClass.SimpleComponent create() {",
                "    return new Builder().build()",
                "  }",
                "",
                "  @SuppressWarnings(\"unchecked\")",
                "  private void initialize(final Builder builder) {",
                "    this.executorProvider =",
                "        TestClass_BModule_ExecutorFactory.create(builder.bModule);",
                "     this.executorProvider2 =",
                "         DoubleCheck.provider(",
                "             TestClass_SimpleComponent_ProductionExecutorModule_ExecutorFactory",
                "                 .create(executorProvider));",
                "    this.simpleComponentProvider = ",
                "        InstanceFactory.<TestClass.SimpleComponent>create(this);",
                "    this.monitorProvider =",
                "        DoubleCheck.provider(",
                "            TestClass_SimpleComponent_MonitoringModule_MonitorFactory.create(",
                "                simpleComponentProvider,",
                "                SetFactory.<ProductionComponentMonitor.Factory>empty());",
                "    this.bProvider = TestClass_BModule_BFactory.create(",
                "        builder.bModule, TestClass_C_Factory.create());",
                "    this.bProducer = Producers.producerFromProvider(bProvider);",
                "    this.aProducer = new TestClass_AModule_AFactory(",
                "        builder.aModule,",
                "        executorProvider2,",
                "        monitorProvider,",
                "        bProducer);",
                "  }",
                "",
                "  @Override",
                "  public ListenableFuture<TestClass.A> a() {",
                "    return aProducer.get();",
                "  }",
                "",
                "  public static final class Builder {",
                "    private TestClass.BModule bModule;",
                "    private TestClass.AModule aModule;",
                "",
                "    private Builder() {",
                "    }",
                "",
                "    public TestClass.SimpleComponent build() {",
                "      if (bModule == null) {",
                "        this.bModule = new TestClass.BModule();",
                "      }",
                "      if (aModule == null) {",
                "        this.aModule = new TestClass.AModule();",
                "      }",
                "      return new DaggerTestClass_SimpleComponent(this);",
                "    }",
                "",
                "    public Builder aModule(TestClass.AModule aModule) {",
                "      this.aModule = Preconditions.checkNotNull(aModule);",
                "      return this;",
                "    }",
                "",
                "    public Builder bModule(TestClass.BModule bModule) {",
                "      this.bModule = Preconditions.checkNotNull(bModule);",
                "      return this;",
                "    }",
                "",
                "    @Deprecated",
                "    public Builder testClass_SimpleComponent_ProductionExecutorModule(",
                "        TestClass_SimpleComponent_ProductionExecutorModule",
                "        testClass_SimpleComponent_ProductionExecutorModule) {",
                "      Preconditions.checkNotNull(testClass_SimpleComponent_ProductionExecutorModule);",
                "      return this;",
                "    }",
                "}");
    }
    assertAbout(javaSource())
        .that(component)
        .withCompilerOptions(compilerMode.javacopts())
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(generatedComponent);
  }

  @Test public void nullableProducersAreNotErrors() {
    JavaFileObject component = JavaFileObjects.forSourceLines("test.TestClass",
        "package test;",
        "",
        "import com.google.common.util.concurrent.ListenableFuture;",
        "import com.google.common.util.concurrent.MoreExecutors;",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import dagger.producers.ProducerModule;",
        "import dagger.producers.Produces;",
        "import dagger.producers.Production;",
        "import dagger.producers.ProductionComponent;",
        "import java.util.concurrent.Executor;",
        "import javax.annotation.Nullable;",
        "import javax.inject.Inject;",
        "",
        "final class TestClass {",
        "  interface A {}",
        "  interface B {}",
        "  interface C {}",
        "",
        "  @Module",
        "  static final class CModule {",
        "    @Provides @Nullable C c() {",
        "      return null;",
        "    }",
        "",
        "    @Provides @Production Executor executor() {",
        "      return MoreExecutors.directExecutor();",
        "    }",
        "  }",
        "",
        "  @ProducerModule",
        "  static final class ABModule {",
        "    @Produces @Nullable B b(@Nullable C c) {",
        "      return null;",
        "    }",

        "    @Produces @Nullable ListenableFuture<A> a(B b) {",  // NOTE: B not injected as nullable
        "      return null;",
        "    }",
        "  }",
        "",
        "  @ProductionComponent(modules = {ABModule.class, CModule.class})",
        "  interface SimpleComponent {",
        "    ListenableFuture<A> a();",
        "  }",
        "}");
    assertAbout(javaSource())
        .that(component)
        .withCompilerOptions(compilerMode.javacopts())
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .withWarningContaining("@Nullable on @Produces methods does not do anything")
        .in(component)
        .onLine(33)
        .and()
        .withWarningContaining("@Nullable on @Produces methods does not do anything")
        .in(component)
        .onLine(36);
  }
}
