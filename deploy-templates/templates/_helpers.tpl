{{- define "keycloak.realmName" -}}
{{- $realm := .realm }}
{{- $release := .release }}
{{- $root := .root }}
    {{- printf "%s-%s" $release.Namespace $realm.name }}
{{- end -}}

{{- define "officer-portal.url" -}}
{{- printf "%s%s" "https://" (.Values.portals.officer.customDns.host | default ( printf "%s-%s-%s.%s" "officer-portal" .Values.cdPipelineName .Values.cdPipelineStageName .Values.dnsWildcard )) }}
{{- end }}

{{- define "citizen-portal.url" -}}
{{- printf "%s%s" "https://" (.Values.portals.citizen.customDns.host | default ( printf "%s-%s-%s.%s" "citizen-portal" .Values.cdPipelineName .Values.cdPipelineStageName .Values.dnsWildcard )) }}
{{- end }}

{{- define "webUrl" -}}
{{- $root := .root }}
{{- $service := .service }}
{{- $dnsWildcard := .dnsWildcard }}
{{- printf "%s%s-%s-%s.%s" "https://" $service.serviceWebUrl $root.cdPipelineName $root.cdPipelineStageName $dnsWildcard }}
{{- end }}

{{- define "keycloak.registryGroup.name" -}}
{{- if .Values.registryGroup }}
{{- if eq .Values.registryGroup.name "" }}
{{- "openshift" }}
{{- else if ne .Values.registryGroup.name "openshift" }}
{{- printf "%s-%s" .Values.registryGroup.name "group" }}
{{- else }}
{{- "openshift" }}
{{- end }}
{{- end }}
{{- end }}

{{- define "keycloak.frontendUrl" }}
{{- $keycloakHost := .Values.keycloak.customHost | default .Values.keycloak.host }}
{{- printf "%s%s%s" "https://" $keycloakHost "/auth/"}}
{{- end }}

{{- define "smtp-internal-password" }}
{{- $secretName := .secretName }}
{{- $namespace := .namespace }}
{{- $secret := (lookup "v1" "Secret" $namespace $secretName) }}
{{- if $secret }}
{{- $secret.data.password }}
{{- else }}
{{- uuidv4 | b64enc }}
{{- end }}
{{- end }}

{{- define "smtp-domain" }}
{{- $smtpServerNamespace := .smtpServerNamespace }}
{{- $smtpDomain := "" }}
{{- range $envVariable := (index (lookup "apps/v1" "Deployment" $smtpServerNamespace "mailu-postfix").spec.template.spec.containers 0).env }}
{{- if eq $envVariable.name "DOMAIN" }}
{{- $smtpDomain = $envVariable.value }}
{{- end }}
{{- end }}
{{- $smtpDomain }}
{{- end }}

{{- define "external-system-api.hostname" -}}
{{- printf "external-service-api-%s" (include "edp.hostnameSuffix" .) }}
{{- end }}

{{- define "admin-tools.hostname" -}}
{{- printf "admin-tools-%s" (include "edp.hostnameSuffix" .) }}
{{- end }}

{{- define "admin-tools.url" -}}
{{- printf "%s%s" "https://" (include "admin-tools.hostname" .) }}
{{- end }}

{{- define "edp.hostnameSuffix" -}}
{{- printf "%s.%s" .Values.stageName .Values.dnsWildcard }}
{{- end }}

{{- define "admin-routes.whitelist.cidr" -}}
{{- if .Values.global }}
{{- if .Values.global.whiteListIP }}
{{- .Values.global.whiteListIP.adminRoutes }}
{{- end }}
{{- end }}
{{- end -}}

{{- define "admin-routes.whitelist.annotation" -}}
haproxy.router.openshift.io/ip_whitelist: {{ (include "admin-routes.whitelist.cidr" . | default "0.0.0.0/0") | quote }}
{{- end -}}

{{- define "trembita-edr-registry-mock-url" -}}
{{- printf "%s%s.%s" "https://" (index .Values.trembitaMock.registries "edr-registry").name .Values.dnsWildcard }}
{{- end }}
