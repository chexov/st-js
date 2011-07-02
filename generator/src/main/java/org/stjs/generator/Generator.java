package org.stjs.generator;

import japa.parser.JavaParser;
import japa.parser.ParseException;
import japa.parser.ast.CompilationUnit;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;

import org.stjs.generator.handlers.ClassOrInterfaceDeclarationHandler;
import org.stjs.generator.handlers.DefaultHandler;
import org.stjs.generator.handlers.FieldDeclarationHandler;
import org.stjs.generator.handlers.InlineFunctionHandler;
import org.stjs.generator.handlers.InlineObjectHandler;
import org.stjs.generator.handlers.MethodDeclarationHandler;
import org.stjs.generator.handlers.NameResolverHandler;
import org.stjs.generator.handlers.RuleBasedVisitor;
import org.stjs.generator.handlers.SkipHandler;
import org.stjs.generator.handlers.VariableDeclarationHandler;
import org.stjs.generator.handlers.VariableTypeHandler;
import org.stjs.generator.scope.FullyQualifiedScope;
import org.stjs.generator.scope.NameResolverVisitor;
import org.stjs.generator.scope.NameScope;
import org.stjs.generator.scope.NameScopeWalker;
import org.stjs.generator.scope.ScopeVisitor;

public class Generator {

	private static MatchingRule rule(String name, String xpath, int priority, DefaultHandler handler) {
		return (new MatchingRule(name, xpath, new NodeHandlerWithPriority(handler, priority)));
	}

	private void rules(RuleBasedVisitor ruleVisitor) {
		// to skip
		ruleVisitor.addRule(rule("Parameter Type", "//Parameter/ReferenceType", 100, new SkipHandler(ruleVisitor)));
		ruleVisitor.addRule(rule("Parameter Type", "//Parameter/PrimitiveType", 100, new SkipHandler(ruleVisitor)));
		ruleVisitor.addRule(rule("Parameter Type", "//PackageDeclaration", 100, new SkipHandler(ruleVisitor)));
		ruleVisitor.addRule(rule("Parameter Type", "//ImportDeclaration", 100, new SkipHandler(ruleVisitor)));

		ruleVisitor.addRule(rule("VariableDeclaration", "//VariableDeclaratorId", 100, new VariableDeclarationHandler(
				ruleVisitor)));

		ruleVisitor.addRule(rule("Variable Type", "//VariableDeclarationExpr/ReferenceType/ClassOrInterfaceType", 100,
				new VariableTypeHandler(ruleVisitor)));
		ruleVisitor.addRule(rule("Variable Type", "//VariableDeclarationExpr/PrimitiveType", 100,
				new VariableTypeHandler(ruleVisitor)));

		ruleVisitor.addRule(rule("Annotations", "//MarkerAnnotationExpr", 100, new SkipHandler(ruleVisitor)));

		ruleVisitor
				.addRule(rule("Method", "//MethodDeclaration", 100, new MethodDeclarationHandler(ruleVisitor, false)));
		ruleVisitor.addRule(rule("Method Params", "//MethodDeclaration/Parameter", 100, new MethodDeclarationHandler(
				ruleVisitor, false)));
		ruleVisitor.addRule(rule("Class/Interface Declaration", "//ClassOrInterfaceDeclaration", 100,
				new ClassOrInterfaceDeclarationHandler(ruleVisitor)));

		// field declaration
		ruleVisitor.addRule(rule("Field", "//FieldDeclaration", 100, new FieldDeclarationHandler(ruleVisitor)));
		ruleVisitor.addRule(rule("Field - Variable Type", "//FieldDeclaration/ReferenceType/ClassOrInterfaceType", 100,
				new FieldDeclarationHandler(ruleVisitor)));
		ruleVisitor.addRule(rule("Field - Variable Type", "//FieldDeclaration/PrimitiveType", 100,
				new FieldDeclarationHandler(ruleVisitor)));
		ruleVisitor.addRule(rule("Field - Variable Type", "//FieldDeclaration/VariableDeclarator", 100,
				new FieldDeclarationHandler(ruleVisitor)));

		// method declaration
		ruleVisitor.addRule(rule("Inline Function", "//ObjectCreationExpr[MethodDeclaration]", 100,
				new InlineFunctionHandler(ruleVisitor)));
		ruleVisitor.addRule(rule("Inline Method", "//ObjectCreationExpr/MethodDeclaration", 200,
				new MethodDeclarationHandler(ruleVisitor, true)));

		// inline obj
		ruleVisitor.addRule(rule("Inline Object Decl", "//ObjectCreationExpr[InitializerDeclaration]", 100,
				new InlineObjectHandler(ruleVisitor)));

		ruleVisitor.addRule(rule("Inline Object Decl", "//ObjectCreationExpr/InitializerDeclaration/BlockStmt", 100,
				new InlineObjectHandler(ruleVisitor)));

		ruleVisitor.addRule(rule("Inline Object Decl",
				"//ObjectCreationExpr/InitializerDeclaration/BlockStmt/ExpressionStmt", 100, new InlineObjectHandler(
						ruleVisitor)));

		ruleVisitor.addRule(rule("Inline Object Decl",
				"//ObjectCreationExpr/InitializerDeclaration/BlockStmt/ExpressionStmt/AssignExpr", 100,
				new InlineObjectHandler(ruleVisitor)));

		// names
		ruleVisitor.addRule(rule("Identifiers", "//NameExpr", 100, new NameResolverHandler(ruleVisitor)));

		ruleVisitor.addRule(rule("Method calls", "//MethodCallExpr", 100, new NameResolverHandler(ruleVisitor)));

	}

	public void generateJavascript(ClassLoader builtProjectClassLoader, Class<?> inputClass, File inputFile,
			File outputFile) throws JavascriptGenerationException {
		InputStream in;
		try {
			in = new FileInputStream(inputFile);
		} catch (FileNotFoundException e) {
			throw new JavascriptGenerationException(inputFile, e);
		}

		RuleBasedVisitor ruleVisitor = new RuleBasedVisitor();

		rules(ruleVisitor);

		try {
			CompilationUnit cu = null;
			// parse the file
			cu = JavaParser.parse(in);

			// resolve first the fields and the methods
			ScopeVisitor scopes = new ScopeVisitor(inputFile, Thread.currentThread().getContextClassLoader());
			NameScope rootScope = new FullyQualifiedScope();
			scopes.visit(cu, rootScope);
			NameResolverVisitor resolver = new NameResolverVisitor(rootScope);
			resolver.visit(cu, new NameScopeWalker(rootScope));

			System.out.println("----------------------------");
			ruleVisitor.generate(cu,
					new GenerationContext(resolver.getResolvedMethods(), resolver.getResolvedIdentifiers()));

			System.out.println("----------------------------");
			FileWriter writer = new FileWriter(outputFile);
			writer.write(ruleVisitor.getSource());
			writer.flush();
			writer.close();
		} catch (ParseException e) {
			throw new JavascriptGenerationException(inputFile, e);
		} catch (IOException e) {
			throw new JavascriptGenerationException(inputFile, e);
		} finally {
			try {
				in.close();
			} catch (IOException e) {
				// silent
				e.printStackTrace();
			}
		}
	}
}