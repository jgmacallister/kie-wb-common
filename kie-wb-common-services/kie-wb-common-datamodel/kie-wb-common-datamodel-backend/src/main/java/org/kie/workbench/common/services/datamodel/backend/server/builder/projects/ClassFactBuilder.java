package org.kie.workbench.common.services.datamodel.backend.server.builder.projects;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.drools.workbench.models.commons.backend.oracle.ProjectDataModelOracleImpl;
import org.drools.workbench.models.datamodel.oracle.Annotation;
import org.drools.workbench.models.datamodel.oracle.MethodInfo;
import org.drools.workbench.models.datamodel.oracle.ModelField;
import org.drools.workbench.models.datamodel.oracle.TypeSource;
import org.kie.workbench.common.services.datamodel.backend.server.builder.util.AnnotationUtils;
import org.kie.workbench.common.services.datamodel.backend.server.builder.util.BlackLists;

/**
 * Builder for Fact Types originating from a .class
 */
public class ClassFactBuilder extends BaseFactBuilder {

    private final ClassToGenericClassConverter typeSystemConverter = new JavaTypeSystemTranslator();

    private final Map<String, List<MethodInfo>> methodInformation = new HashMap<String, List<MethodInfo>>();
    private final Map<String, String> fieldParametersType = new HashMap<String, String>();

    private final List<String> superTypes;
    private final Set<Annotation> annotations = new LinkedHashSet<Annotation>();
    private final Map<String, Set<Annotation>> fieldAnnotations = new HashMap<String, Set<Annotation>>();

    private final Map<String, FactBuilder> fieldFactBuilders = new HashMap<String, FactBuilder>();

    public ClassFactBuilder( final ProjectDataModelOracleBuilder builder,
                             final Class<?> clazz,
                             final boolean isEvent,
                             final TypeSource typeSource ) throws IOException {
        this( builder,
              new HashMap<String, FactBuilder>(),
              clazz,
              isEvent,
              typeSource );
    }

    public ClassFactBuilder( final ProjectDataModelOracleBuilder builder,
                             final Map<String, FactBuilder> discoveredFieldFactBuilders,
                             final Class<?> clazz,
                             final boolean isEvent,
                             final TypeSource typeSource ) throws IOException {
        super( builder,
               clazz,
               isEvent,
               typeSource );
        this.superTypes = getSuperTypes( clazz );
        this.annotations.addAll( getAnnotations( clazz ) );
        this.fieldAnnotations.putAll( getFieldsAnnotations( clazz ) );
        loadClassFields( clazz,
                         discoveredFieldFactBuilders );
    }

    @Override
    public void build( final ProjectDataModelOracleImpl oracle ) {
        super.build( oracle );
        oracle.addProjectMethodInformation( methodInformation );
        oracle.addProjectFieldParametersType( fieldParametersType );
        oracle.addProjectSuperTypes( buildSuperTypes() );
        oracle.addProjectTypeAnnotations( buildTypeAnnotations() );
        oracle.addProjectTypeFieldsAnnotations( buildTypeFieldsAnnotations() );
    }

    private List<String> getSuperTypes( final Class<?> clazz ) {
        ArrayList<String> strings = new ArrayList<String>();
        Class<?> superType = clazz.getSuperclass();
        while ( superType != null ) {
            strings.add( superType.getName() );
            superType = superType.getSuperclass();
        }

        return strings;
    }

    protected Set<Annotation> getAnnotations( final Class<?> clazz ) {
        final Set<Annotation> dmoAnnotations = new LinkedHashSet<Annotation>();
        final java.lang.annotation.Annotation annotations[] = clazz.getAnnotations();
        for ( java.lang.annotation.Annotation a : annotations ) {
            final Annotation dmoa = new Annotation( a.annotationType().getName() );
            for ( Method m : a.annotationType().getDeclaredMethods() ) {
                final String methodName = m.getName();
                dmoa.addAttribute( methodName,
                                   AnnotationUtils.getAnnotationAttributeValue( a,
                                                                                methodName ) );
            }
            dmoAnnotations.add( dmoa );
        }
        return dmoAnnotations;
    }

    private Map<String, Set<Annotation>> getFieldsAnnotations( final Class<?> clazz ) {
        final Field[] fields = clazz.getDeclaredFields();
        final Map<String, Set<Annotation>> fieldsAnnotations = new HashMap<String, Set<Annotation>>();
        for ( Field field : fields ) {
            final String fieldName = field.getName();
            final Set<Annotation> fieldAnnotations = getFieldAnnotations( field );
            if ( fieldAnnotations.size() > 0 ) {
                fieldsAnnotations.put( fieldName,
                                       fieldAnnotations );
            }
        }
        return fieldsAnnotations;
    }

    private Set<Annotation> getFieldAnnotations( final Field field ) {
        final java.lang.annotation.Annotation[] annotations = field.getDeclaredAnnotations();
        final Set<Annotation> fieldAnnotations = new LinkedHashSet<Annotation>();
        for ( java.lang.annotation.Annotation a : annotations ) {
            final Annotation fieldAnnotation = new Annotation( a.annotationType().getName() );
            for ( Method m : a.annotationType().getDeclaredMethods() ) {
                final String methodName = m.getName();
                fieldAnnotation.addAttribute( methodName,
                                              AnnotationUtils.getAnnotationAttributeValue( a,
                                                                                           methodName ) );
            }
            fieldAnnotations.add( fieldAnnotation );
        }
        return fieldAnnotations;
    }

    private void loadClassFields( final Class<?> clazz,
                                  final Map<String, FactBuilder> discoveredFieldFactBuilders ) throws IOException {
        if ( clazz == null ) {
            return;
        }

        final String factType = getType();

        //Get all getters and setters for the class. This does not handle delegated properties
        //- FIELDS need a getter ("getXXX", "isXXX") or setter ("setXXX") or are public properties
        //- METHODS are any accessor that does not have a getter or setter
        final ClassFieldInspector inspector = new ClassFieldInspector( clazz );
        final Set<String> fieldNames = inspector.getFieldNames();

        for ( final String fieldName : fieldNames ) {
            final ClassFieldInspector.FieldInfo f = inspector.getFieldTypesFieldInfo().get( fieldName );
            addParametricTypeForField( factType,
                                       fieldName,
                                       f.getGenericType() );

            final Class<?> returnType = f.getReturnType();
            final String genericReturnType = typeSystemConverter.translateClassToGenericType( returnType );

            addField( new ModelField( fieldName,
                                      returnType.getName(),
                                      ModelField.FIELD_CLASS_TYPE.REGULAR_CLASS,
                                      f.getOrigin(),
                                      f.getAccessorAndMutator(),
                                      genericReturnType ) );

            addEnumsForField( factType,
                              fieldName,
                              returnType );

            //To prevent recursion we keep track of all ClassFactBuilder's created and re-use where applicable
            if ( BlackLists.isReturnTypeBlackListed( returnType ) ) {
                continue;
            }
            if ( !discoveredFieldFactBuilders.containsKey( genericReturnType ) ) {
                discoveredFieldFactBuilders.put( genericReturnType,
                                                 null );
                discoveredFieldFactBuilders.put( genericReturnType,
                                                 new ClassFactBuilder( builder,
                                                                       discoveredFieldFactBuilders,
                                                                       returnType,
                                                                       false,
                                                                       typeSource ) );
            }
            if ( discoveredFieldFactBuilders.get( genericReturnType ) != null ) {
                fieldFactBuilders.put( genericReturnType,
                                       discoveredFieldFactBuilders.get( genericReturnType ) );
            }
        }

        //Methods for use in Expressions and ActionCallMethod's
        ClassMethodInspector methodInspector = new ClassMethodInspector( clazz,
                                                                         typeSystemConverter );

        final List<MethodInfo> methodInformation = methodInspector.getMethodInfos();
        for ( final MethodInfo mi : methodInformation ) {
            final String genericType = mi.getParametricReturnType();
            if ( genericType != null ) {
                final String qualifiedFactFieldName = factType + "#" + mi.getNameWithParameters();
                this.fieldParametersType.put( qualifiedFactFieldName,
                                              genericType );
            }
        }
        this.methodInformation.put( factType,
                                    methodInformation );
    }

    private void addEnumsForField( final String className,
                                   final String fieldName,
                                   final Class<?> fieldClazz ) {
        if ( fieldClazz.isEnum() ) {
            final Field[] enumFields = fieldClazz.getDeclaredFields();
            final List<String> enumValues = new ArrayList<String>();
            for ( final Field enumField : enumFields ) {
                if ( enumField.isEnumConstant() ) {
                    String shortName = fieldClazz.getName().substring( fieldClazz.getName().lastIndexOf( "." ) + 1 ) + "." + enumField.getName();
                    if ( shortName.contains( "$" ) ) {
                        shortName = shortName.replaceAll( "\\$",
                                                          "." );
                    }
                    enumValues.add( shortName + "=" + shortName );
                }
            }
            final String a[] = new String[ enumValues.size() ];
            enumValues.toArray( a );
            getDataModelBuilder().addEnum( className,
                                           fieldName,
                                           a );
        }
    }

    private void addParametricTypeForField( final String className,
                                            final String fieldName,
                                            final Type type ) {
        final String qualifiedFactFieldName = className + "#" + fieldName;
        final String parametricType = getParametricType( type );
        if ( parametricType != null ) {
            fieldParametersType.put( qualifiedFactFieldName,
                                     parametricType );
        }
    }

    private String getParametricType( final Type type ) {
        if ( type instanceof ParameterizedType ) {
            final ParameterizedType pt = (ParameterizedType) type;
            Type parameter = null;
            for ( final Type t : pt.getActualTypeArguments() ) {
                parameter = t;
            }
            if ( parameter != null ) {
                if ( parameter instanceof Class<?> ) {
                    return ( (Class<?>) parameter ).getName();
                }
                return null;
            } else {
                return null;
            }
        }
        return null;
    }

    private Map<String, List<String>> buildSuperTypes() {
        final Map<String, List<String>> loadableSuperTypes = new HashMap<String, List<String>>();
        loadableSuperTypes.put( getType(),
                                superTypes );
        return loadableSuperTypes;
    }

    private Map<String, Set<Annotation>> buildTypeAnnotations() {
        final Map<String, Set<Annotation>> loadableTypeAnnotations = new HashMap<String, Set<Annotation>>();
        loadableTypeAnnotations.put( getType(),
                                     annotations );
        return loadableTypeAnnotations;
    }

    private Map<String, Map<String, Set<Annotation>>> buildTypeFieldsAnnotations() {
        final Map<String, Map<String, Set<Annotation>>> loadableTypeFieldsAnnotations = new HashMap<String, Map<String, Set<Annotation>>>();
        loadableTypeFieldsAnnotations.put( getType(),
                                           fieldAnnotations );
        return loadableTypeFieldsAnnotations;
    }

    @Override
    public Map<String, FactBuilder> getInternalBuilders() {
        for ( final FactBuilder factBuilder : new ArrayList<FactBuilder>( this.fieldFactBuilders.values() ) ) {
            this.fieldFactBuilders.putAll( factBuilder.getInternalBuilders() );
        }
        return fieldFactBuilders;
    }
}
