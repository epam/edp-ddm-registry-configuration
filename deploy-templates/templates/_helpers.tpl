{{- define "keycloak.realmName" -}}
{{- $realm := .realm }}
{{- $release := .release }}
{{- $root := .root }}
    {{- printf "%s-%s" $release.Namespace $realm.name }}
{{- end -}}

{{- define "portal.default.host" }}
{{- $root := .root }}
{{- $portalName := .portalName }}
{{- printf "%s-%s-%s.%s" $portalName $root.Values.cdPipelineName $root.Values.cdPipelineStageName $root.Values.dnsWildcard }}
{{- end }}

{{- define "officer-portal.url" -}}
{{ $host := ternary .Values.portals.officer.customDns.host (include "portal.default.host" (dict  "root" . "portalName" "officer-portal")) .Values.portals.officer.customDns.enabled }}
{{- printf "%s%s" "https://" $host }}
{{- end }}

{{- define "citizen-portal.url" -}}
{{ $host := ternary .Values.portals.citizen.customDns.host (include "portal.default.host" (dict  "root" . "portalName" "citizen-portal")) .Values.portals.citizen.customDns.enabled }}
{{- printf "%s%s" "https://" $host }}
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

{{- define "codebases.release.name" }}
{{- ternary "codebases" "codebase-operator" (eq .Values.edpProject "control-plane") }}
{{- end }}

{{- define "assets.logoHeader" -}}
{{- $configmap := (lookup "v1" "ConfigMap" .Release.Namespace "registry-logos") -}}
{{- if $configmap -}}
{{- index $configmap.binaryData "header-logo.svg" -}}
{{- else -}}
{{- .Values.assets.logoHeader }}
{{- end -}}
{{- end -}}

{{- define "assets.logoLoader" -}}
{{- $configmap := (lookup "v1" "ConfigMap" .Release.Namespace "registry-logos") -}}
{{- if $configmap -}}
{{- index $configmap.binaryData "loader-logo.svg" -}}
{{- else -}}
{{- .Values.assets.logoLoader }}
{{- end -}}
{{- end -}}

{{- define "assets.logoFavicon" -}}
{{- $configmap := (lookup "v1" "ConfigMap" .Release.Namespace "registry-logos") -}}
{{- if $configmap -}}
{{- index $configmap.binaryData "favicon.png" -}}
{{- else -}}
{{- .Values.assets.logoFavicon }}
{{- end -}}
{{- end -}}

{{- define "redisServiceName" -}}
{{- $generated := get .Values.kongRedis "_generatedRedisServiceName" | default "" -}}
{{- if not $generated -}}
{{- $suffix := randAlpha 5 | lower -}}
{{- $name := printf "%s-%s" .Values.kongRedis.serviceName $suffix -}}
{{- $_ := set .Values.kongRedis "_generatedRedisServiceName" $name -}}
{{- end -}}
{{- get .Values.kongRedis "_generatedRedisServiceName" -}}
{{- end -}}

{{- define "externalS3.enabled" -}}
{{- if (hasKey .Values.global "externalS3") -}}
true
{{- else -}}
false
{{- end -}}
{{- end -}}

{{- define "externalS3.validate" -}}
{{- $ext := .Values.global.externalS3 -}}
{{- if not $ext.endpoint -}}
  {{- fail "global.externalS3.endpoint is required" -}}
{{- end -}}
{{- if not $ext.buckets -}}
  {{- fail "global.externalS3.buckets must be defined" -}}
{{- end -}}
{{- if not (gt (len $ext.buckets) 0) -}}
  {{- fail "global.externalS3.buckets must contain at least one of: file, datafactory" -}}
{{- end -}}
{{- $valid := list "file" "datafactory" -}}
{{- $ns := .Release.Namespace -}}
{{- range $k, $cfg := $ext.buckets -}}
  {{- if not (has $k $valid) -}}
    {{- fail (printf "Invalid bucket key '%s'. Allowed keys: file, datafactory" $k) -}}
  {{- end -}}
  {{- if or (not (hasKey $cfg "name")) (eq (toString $cfg.name | trim) "") -}}
    {{- fail (printf "global.externalS3.buckets.%s.name is required" $k) -}}
  {{- end -}}
  {{- if or (not (hasKey $cfg "secretRef")) (eq (tpl (toString (index $cfg "secretRef")) $ | trim) "") -}}
    {{- fail (printf "global.externalS3.buckets.%s.secretRef is required" $k) -}}
  {{- end -}}
  {{- $secretName := tpl (toString $cfg.secretRef) $ | trim -}}
  {{- $secret := lookup "v1" "Secret" $ns $secretName -}}
  {{- if not $secret -}}
    {{- fail (printf "Secret %s not found in namespace %s" $secretName $ns) -}}
  {{- end -}}
  {{- $d := $secret.data -}}
  {{- if or (not $d) (not (hasKey $d "AWS_ACCESS_KEY_ID")) -}}
    {{- fail (printf "Secret %s must contain AWS_ACCESS_KEY_ID" $secretName) -}}
  {{- end -}}
  {{- if eq (trim (b64dec (index $d "AWS_ACCESS_KEY_ID"))) "" -}}
    {{- fail (printf "Secret %s must contain non-empty AWS_ACCESS_KEY_ID" $secretName) -}}
  {{- end -}}
  {{- if or (not $d) (not (hasKey $d "AWS_SECRET_ACCESS_KEY")) -}}
    {{- fail (printf "Secret %s must contain AWS_SECRET_ACCESS_KEY" $secretName) -}}
  {{- end -}}
  {{- if eq (trim (b64dec (index $d "AWS_SECRET_ACCESS_KEY"))) "" -}}
    {{- fail (printf "Secret %s must contain non-empty AWS_SECRET_ACCESS_KEY" $secretName) -}}
  {{- end -}}
{{- end -}}
{{- end -}}

{{- define "externalS3.endpoint" -}}
{{- required "global.externalS3.endpoint is required" .Values.global.externalS3.endpoint -}}
{{- end -}}

{{- define "externalS3.authority" -}}
{{- $ep := include "externalS3.endpoint" . | trim -}}
{{- $noScheme := $ep | trimPrefix "https://" | trimPrefix "http://" -}}
{{- index (splitList "/" $noScheme) 0 -}}
{{- end -}}

{{- define "externalS3.port" -}}
{{- $parts := .parts -}}
{{- if gt (len $parts) 1 -}}
{{- index $parts 1 -}}
{{- else -}}
443
{{- end -}}
{{- end -}}

{{- define "externalS3.scheme" -}}
{{- $vals := .Values | default dict -}}
{{- $global := (hasKey $vals "global") | ternary $vals.global (dict) -}}
{{- $ext := (hasKey $global "externalS3") | ternary $global.externalS3 (dict) -}}
{{- $ca := (hasKey $ext "caSecretRef") | ternary (tpl (toString $ext.caSecretRef) . | trim) "" -}}
{{- if ne $ca "" -}}
http
{{- else -}}
https
{{- end -}}
{{- end -}}

{{- define "externalS3.effectivePort" -}}
{{- $root := .root -}}
{{- $parts := .parts -}}
{{- $vals := $root.Values | default dict -}}
{{- $global := (hasKey $vals "global") | ternary $vals.global (dict) -}}
{{- $ext := (hasKey $global "externalS3") | ternary $global.externalS3 (dict) -}}
{{- $ca := (hasKey $ext "caSecretRef") | ternary (tpl (toString $ext.caSecretRef) $root | trim) "" -}}
{{- if ne $ca "" -}}
80
{{- else -}}
{{- include "externalS3.port" (dict "parts" $parts) -}}
{{- end -}}
{{- end -}}

{{- define "externalS3.bucketName" -}}
{{- $map := dict
    "datafactory"     "datafactory-external-s3-bucket"
    "file"            "file-external-s3-bucket"
-}}
{{- index $map . -}}
{{- end -}}
