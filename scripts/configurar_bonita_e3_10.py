#!/usr/bin/env python3
"""Configura E3-10 en el BOS de E3-09 sin tocar el workspace ni desplegar procesos.

Uso desde la raíz: python3 scripts/configurar_bonita_e3_10.py [--check]
Requiere haber construido bonita-sistema-nacional-connector con Maven.
Conserva los miembros ajenos a E3-10; el cambio del diagrama es idempotente.
"""

import argparse
from pathlib import Path
import tempfile
import zipfile
from xml.dom import minidom

ROOT = Path(__file__).resolve().parents[1]
MODULE = "bonita-sistema-nacional-connector"
VERSION = "1.0.0-SNAPSHOT"
PREFIX = "_E310_"
XMI = "http://www.omg.org/XMI"
TASKS = {
    "Evaluar ofertas": ("EVALUAR", "ofertasConsolidadasJson", "ofertasEvaluadasJson"),
    "Comprometer recursos": ("COMPROMETER", "ofertasEvaluadasJson", "compromisoNacionalJson"),
    "Liberar recursos": ("LIBERAR", "ofertasEvaluadasJson", "liberacionNacionalJson"),
}
VARIABLES = {
    "sistemaNacionalBaseUrl": ("java.lang.String", "http://localhost:8082"),
    "ofertasEvaluadasJson": ("java.lang.String", "[]"),
    "compromisoNacionalJson": ("java.lang.String", "{}"),
    "liberacionNacionalJson": ("java.lang.String", "{}"),
    "ofertasProcesadasJson": ("java.lang.String", "[]"),
    "ofertaSeleccionadaId": ("java.lang.Long", "0"),
    "hayOfertasPendientes": ("java.lang.Boolean", "false"),
    "continuarSeleccionando": ("java.lang.Boolean", "true"),
}


def children(node, tag):
    return [n for n in node.childNodes if n.nodeType == n.ELEMENT_NODE and n.tagName == tag]


def only(nodes):
    if len(nodes) != 1:
        raise ValueError(f"Se esperaba un elemento único, se encontraron {len(nodes)}")
    return nodes[0]


def configure_diagram(source):
    doc = minidom.parseString(source)
    pool = only([n for n in doc.getElementsByTagName("elements")
                 if n.getAttribute("xmi:type") == "process:Pool" and n.getAttribute("name") == "RescueSync"])
    if doc.getElementsByTagName("process:MainProcess")[0].getAttribute("bonitaModelVersion") != "9":
        raise ValueError("Este script corresponde al modelo 9 de Bonita 10.4.0")
    for node in list(doc.getElementsByTagName("*")):
        if node.getAttribute("xmi:id").startswith(PREFIX) and node.parentNode:
            node.parentNode.removeChild(node)
    pool.setAttribute("version", "1.2")
    types = {}
    for variable, java_type in [("emergenciaId", "java.lang.Long"), ("ofertasConsolidadasJson", "java.lang.String"),
                                ("coberturaCompleta", "java.lang.Boolean")]:
        data = only([n for n in children(pool, "data") if n.getAttribute("name") == variable])
        types[java_type] = data.getAttribute("dataType")
    var_types = {"emergenciaId": "java.lang.Long", "ofertasConsolidadasJson": "java.lang.String"}
    var_types.update({name: data[0] for name, data in VARIABLES.items()})
    counter = 0

    def node(tag, xmi_type, **attributes):
        nonlocal counter
        counter += 1
        element = doc.createElement(tag)
        # type y xmi:type son atributos distintos; minidom necesita sus namespaces
        # para no reemplazar el tipo EMF al agregar el tipo de dato/operador.
        element.setAttributeNS(XMI, "xmi:type", xmi_type)
        element.setAttributeNS(XMI, "xmi:id", PREFIX + str(counter))
        for key, value in attributes.items():
            element.setAttribute(key, str(value))
        return element

    def ref_variable(expression, name):
        expression.appendChild(node("referencedElements", "process:Data", name=name, dataType=types[var_types[name]]))

    def expr(tag, name, content, java_type, kind=None, refs=()):
        result = node(tag, "expression:Expression", name=name, content=content, returnType=java_type, returnTypeFixed="true")
        if kind:
            result.setAttribute("type", kind)
        if kind == "TYPE_READ_ONLY_SCRIPT":
            result.setAttribute("interpreter", "GROOVY")
        for ref in refs:
            ref_variable(result, ref)
        return result

    def operation(parent, tag, target, right):
        op = node(tag, "expression:Operation")
        op.appendChild(expr("leftOperand", target, target, var_types[target], "TYPE_VARIABLE", [target]))
        op.appendChild(right)
        op.appendChild(node("operator", "expression:Operator", type="ASSIGNMENT"))
        parent.appendChild(op)

    for name, (java_type, initial) in VARIABLES.items():
        data = node("data", "process:Data", name=name, dataType=types[java_type])
        data.appendChild(expr("defaultValue", initial, initial, java_type))
        pool.appendChild(data)

    for task_name, (action, offers, output) in TASKS.items():
        task = only([n for n in doc.getElementsByTagName("elements") if n.getAttribute("name") == task_name])
        connector = node("connectors", "process:Connector", name="sistemaNacional" + action.title(),
                         definitionId=MODULE, definitionVersion="1.0.0", event="ON_ENTER", ignoreErrors="false")
        configuration = node("configuration", "connectorconfiguration:ConnectorConfiguration",
                             definitionId=MODULE, version="1.0.0", modelVersion="9")
        connector.appendChild(configuration)
        params = {"operacion": (action, False), "sistemaNacionalBaseUrl": ("sistemaNacionalBaseUrl", True),
                  "emergenciaId": ("emergenciaId", True), "ofertasJson": (offers, True),
                  "ofertasProcesadasJson": ("ofertasProcesadasJson", True)}
        if action != "EVALUAR":
            params["ofertaSeleccionadaId"] = ("ofertaSeleccionadaId", True)
        for key, (value, variable) in params.items():
            param = node("parameters", "connectorconfiguration:ConnectorParameter", key=key)
            param.appendChild(expr("expression", value, value, var_types[value] if variable else "java.lang.String",
                                   "TYPE_VARIABLE" if variable else None, [value] if variable else []))
            configuration.appendChild(param)
        outputs = {"resultadoJson": output}
        if action in ("EVALUAR", "LIBERAR"):
            outputs.update({"ofertasProcesadasJson": "ofertasProcesadasJson", "hayOfertasPendientes": "hayOfertasPendientes"})
        for source_name, target in outputs.items():
            right = expr("rightOperand", source_name, source_name, var_types[target], "CONNECTOR_OUTPUT_TYPE")
            right.appendChild(node("referencedElements", "definition:Output", name=source_name, type=var_types[target]))
            operation(connector, "outputs", target, right)
        task.appendChild(connector)

    # Entrada mínima de la tarea humana existente: permite elegir una oferta, sin adjudicar automáticamente.
    selection = only([n for n in doc.getElementsByTagName("elements") if n.getAttribute("name") == "Seleccionar oferta"])
    contract = only(children(selection, "contract"))
    for name, kind, description in [
        ("ofertaSeleccionadaIdInput", "LONG", "ID de una oferta evaluada y pendiente de esta emergencia"),
        ("continuarSeleccionandoInput", "BOOLEAN", "Elegir otra oferta después de finalizar esta actividad")
    ]:
        if any(n.getAttribute("name") == name for n in children(contract, "inputs")):
            raise ValueError("La selección ya tiene un contrato no generado por E3-10; revisar antes de combinar")
        entry = node("inputs", "process:ContractInput", name=name, type=kind, description=description)
        entry.appendChild(node("mapping", "process:ContractInputMapping"))
        contract.appendChild(entry)
    constraint = node("constraints", "process:ContractConstraint", name="seleccionObligatoria",
                      expression="ofertaSeleccionadaIdInput != null && ofertaSeleccionadaIdInput > 0 && continuarSeleccionandoInput != null",
                      errorMessage="Indique una oferta válida y si desea continuar seleccionando.")
    for name in ["ofertaSeleccionadaIdInput", "continuarSeleccionandoInput"]:
        entry = doc.createElement("inputNames")
        entry.appendChild(doc.createTextNode(name))
        constraint.appendChild(entry)
    contract.appendChild(constraint)

    script = """def lotes = new groovy.json.JsonSlurper().parseText(ofertasEvaluadasJson)
def procesadas = new groovy.json.JsonSlurper().parseText(ofertasProcesadasJson)
def ofertas = lotes.collectMany { it.ofertas }
if (!ofertas.any { it.id == ofertaSeleccionadaIdInput } || procesadas.any { it == ofertaSeleccionadaIdInput }) {
    throw new IllegalArgumentException('La oferta debe pertenecer a esta emergencia y estar pendiente')
}
return ofertaSeleccionadaIdInput"""
    right = expr("rightOperand", "Validar oferta seleccionada", script, "java.lang.Long", "TYPE_READ_ONLY_SCRIPT",
                 ["ofertasEvaluadasJson", "ofertasProcesadasJson"])
    right.appendChild(node("referencedElements", "process:ContractInput", name="ofertaSeleccionadaIdInput", type="LONG"))
    operation(selection, "operations", "ofertaSeleccionadaId", right)
    right = expr("rightOperand", "continuarSeleccionandoInput", "continuarSeleccionandoInput", "java.lang.Boolean", "TYPE_CONTRACT_INPUT")
    right.appendChild(node("referencedElements", "process:ContractInput", name="continuarSeleccionandoInput", type="BOOLEAN"))
    operation(selection, "operations", "continuarSeleccionando", right)

    # El gateway de selección antes usaba coberturaCompleta en ambas salidas. Ahora depende de estas ofertas.
    for flow_id, negate in [("_hXTT8K1uEfGW0c0QtHsXpQ", False), ("_nfN38K1vEfGvDr4EHes88Q", True)]:
        flow = only([n for n in doc.getElementsByTagName("connections") if n.getAttribute("xmi:id") == flow_id])
        old = only(children(flow, "condition"))
        condition = "hayOfertasPendientes && continuarSeleccionando"
        if negate:
            condition = "!(" + condition + ")"
        new = expr("condition", "Quedan ofertas por seleccionar", "return " + condition, "java.lang.Boolean",
                   "TYPE_READ_ONLY_SCRIPT", ["hayOfertasPendientes", "continuarSeleccionando"])
        new.setAttributeNS(XMI, "xmi:id", old.getAttribute("xmi:id"))
        flow.replaceChild(new, old)
    return doc.toxml(encoding="UTF-8")


def configure_pom(source):
    doc = minidom.parseString(source)
    # El parent Bonita administra otras versiones de Jackson; ambos conectores
    # comparten classpath y E3-09 declara core 2.17.2 en su implementación.
    managements = children(doc.documentElement, "dependencyManagement")
    if managements:
        management = only(managements)
    else:
        management = doc.createElement("dependencyManagement")
        doc.documentElement.appendChild(management)
        management.appendChild(doc.createElement("dependencies"))
    managed = only(children(management, "dependencies"))
    jackson = [n for n in children(managed, "dependency")
               if only(children(n, "artifactId")).firstChild.nodeValue == "jackson-bom"]
    if not jackson:
        dependency = doc.createElement("dependency")
        for name, value in [("groupId", "com.fasterxml.jackson"), ("artifactId", "jackson-bom"),
                            ("version", "2.17.2"), ("type", "pom"), ("scope", "import")]:
            item = doc.createElement(name)
            item.appendChild(doc.createTextNode(value))
            dependency.appendChild(item)
        managed.appendChild(dependency)
    dependencies = only(children(doc.documentElement, "dependencies"))
    for dependency in children(dependencies, "dependency"):
        artifacts = children(dependency, "artifactId")
        if artifacts and artifacts[0].firstChild.nodeValue == MODULE:
            return doc.toxml(encoding="UTF-8")
    dependency = doc.createElement("dependency")
    for name, value in [("groupId", "com.grupo21.rescuesync.bonita"), ("artifactId", MODULE), ("version", VERSION)]:
        item = doc.createElement(name)
        item.appendChild(doc.createTextNode(value))
        dependency.appendChild(item)
    dependencies.appendChild(dependency)
    return doc.toxml(encoding="UTF-8")


def configure_implementations(source):
    doc = minidom.parseString(source)
    # Studio puede inferirlos al configurar, pero el build Maven necesita el mapeo explícito.
    for module in ("bonita-ofertas-consolidadas-connector", MODULE):
        mappings = [n for n in children(doc.documentElement, "definitionMappings")
                    if n.getAttribute("definitionId") == module]
        if mappings:
            mapping = only(mappings)
        else:
            mapping = doc.createElement("definitionMappings")
            doc.documentElement.appendChild(mapping)
        for name, value in {"type": "CONNECTOR", "definitionId": module, "definitionVersion": "1.0.0",
                            "implementationId": module + "-impl", "implementationVersion": VERSION}.items():
            mapping.setAttribute(name, value)
    return doc.toxml(encoding="UTF-8")


def verify(archive):
    with zipfile.ZipFile(archive) as z:
        proc = only([n for n in z.namelist() if n.endswith(".proc")])
        doc = minidom.parseString(z.read(proc))
        ids = [n.getAttribute("xmi:id") for n in doc.getElementsByTagName("*") if n.hasAttribute("xmi:id")]
        assert len(ids) == len(set(ids)), "IDs XMI repetidos"
        for element in doc.getElementsByTagName("*"):
            if element.getAttributeNS(XMI, "id").startswith(PREFIX):
                assert element.getAttributeNS(XMI, "type"), "Falta el tipo EMF en " + element.tagName
        for task_name, (action, _, output) in TASKS.items():
            task = only([n for n in doc.getElementsByTagName("elements") if n.getAttribute("name") == task_name])
            connector = only([n for n in children(task, "connectors") if n.getAttribute("definitionId") == MODULE])
            assert connector.getAttribute("event") == "ON_ENTER"
            assert connector.getAttribute("ignoreErrors") == "false"
            params = {n.getAttribute("key"): only(children(n, "expression")) for n in connector.getElementsByTagName("parameters")}
            assert params["operacion"].getAttribute("content") == action
            assert params["emergenciaId"].getAttribute("content") == "emergenciaId"
            assert any(n.getAttribute("content") == output for n in connector.getElementsByTagName("leftOperand"))
        assert any(n.getAttribute("name") == "consultarOfertasConsolidadas" for n in doc.getElementsByTagName("connectors"))
        assert any(n.endswith(f"/{MODULE}-{VERSION}.jar") for n in z.namelist())
        assert any(n.endswith(f"/{MODULE}-{VERSION}.pom") for n in z.namelist())
        assert configure_diagram(z.read(proc)) == z.read(proc), "Configuración no idempotente"
        app = proc.split("/diagrams/")[0]
        assert configure_pom(z.read(app + "/pom.xml")) == z.read(app + "/pom.xml")
        configurations = [n for n in z.namelist() if "/process_configurations/" in n and n.endswith(".conf")]
        config = only(configurations)
        assert configure_implementations(z.read(config)) == z.read(config), "Faltan mapeos de implementación"
    print("BOS E3-10: tres conectores, contrato, variables, IDs y configuración idempotente verificados.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    archive = ROOT / "rescue-sync.bos"
    if args.check:
        verify(archive)
        return
    module = ROOT / MODULE
    jar = module / "target" / f"{MODULE}-{VERSION}.jar"
    if not jar.is_file():
        raise SystemExit("Primero ejecutar mvn package en " + MODULE)
    with zipfile.ZipFile(archive) as z:
        members = {n: z.read(n) for n in z.namelist()}
        metadata = {n: z.getinfo(n) for n in z.namelist()}
    proc = only([n for n in members if n.endswith(".proc")])
    app = proc.split("/diagrams/")[0]
    members[proc] = configure_diagram(members[proc])
    members[app + "/pom.xml"] = configure_pom(members[app + "/pom.xml"])
    config = only([n for n in members if "/process_configurations/" in n and n.endswith(".conf")])
    members[config] = configure_implementations(members[config])
    store = app + f"/.store/com/grupo21/rescuesync/bonita/{MODULE}/{VERSION}/{MODULE}-{VERSION}"
    members[store + ".jar"] = jar.read_bytes()
    members[store + ".pom"] = (module / "pom.xml").read_bytes()
    with tempfile.TemporaryDirectory() as temporary:
        output = Path(temporary) / "rescue-sync.bos"
        with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as z:
            for name, data in members.items():
                z.writestr(metadata.get(name, name), data)
        verify(output)
        archive.write_bytes(output.read_bytes())
    print("Actualizado rescue-sync.bos (RescueSync 1.2). No se desplegó ni modificó un workspace de Studio.")


if __name__ == "__main__":
    main()
