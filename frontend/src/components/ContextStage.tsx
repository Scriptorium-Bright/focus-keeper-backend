import React, { useEffect, useState } from 'react';
import { View, Text, StyleSheet, TextInput, TouchableOpacity, ScrollView } from 'react-native';

interface ContextStageProps {
  active: boolean;
  userId: string;
  metricDate: string;
  onSave: (userId: string, metricDate: string) => void;
  onStageChange: (stage: number) => void;
}

export function ContextStage({
  active,
  userId,
  metricDate,
  onSave,
  onStageChange,
}: ContextStageProps) {
  const [draftUserId, setDraftUserId] = useState(userId);
  const [draftMetricDate, setDraftMetricDate] = useState(metricDate);

  useEffect(() => {
    setDraftUserId(userId);
    setDraftMetricDate(metricDate);
  }, [userId, metricDate]);

  if (!active) return null;

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <View style={styles.stageIntro}>
        <Text style={styles.eyebrow}>01 CONTEXT</Text>
        <Text style={styles.h2}>누가, 어느 날짜 기준으로 복귀 루프를 검증할지 먼저 고정합니다.</Text>
        <Text style={styles.note}>저장된 컨텍스트는 이후 모든 API 호출과 인사이트 조회에 공통으로 사용됩니다.</Text>
      </View>

      <View style={styles.card}>
        <View style={styles.cardHead}>
          <Text style={styles.eyebrow}>USER SETUP</Text>
          <Text style={styles.cardTitle}>검증 컨텍스트</Text>
          <Text style={styles.note}>브라우저/앱에 저장되며 다음 방문에서도 유지됩니다.</Text>
        </View>

        <View style={styles.form}>
          <View style={styles.field}>
            <Text style={styles.label}>userId</Text>
            <TextInput
              style={styles.input}
              placeholder="demo-user"
              value={draftUserId}
              onChangeText={setDraftUserId}
              autoCapitalize="none"
            />
          </View>
          <View style={styles.field}>
            <Text style={styles.label}>metricDate (YYYY-MM-DD)</Text>
            <TextInput
              style={styles.input}
              placeholder="2024-05-28"
              value={draftMetricDate}
              onChangeText={setDraftMetricDate}
              keyboardType="numbers-and-punctuation"
            />
          </View>
          <TouchableOpacity
            style={styles.primaryButton}
            onPress={() => onSave(draftUserId, draftMetricDate)}
          >
            <Text style={styles.primaryButtonText}>컨텍스트 저장</Text>
          </TouchableOpacity>
        </View>
      </View>

      <View style={styles.card}>
        <View style={styles.cardHead}>
          <Text style={styles.eyebrow}>SCENARIO</Text>
          <Text style={styles.cardTitle}>오늘의 검증 흐름</Text>
        </View>
        <View style={styles.scenarioList}>
          <ScenarioStep index={1} title="계획 입력">
            머릿속 작업을 Inbox로 적고, 오늘 다시 붙잡을 Big 3를 고릅니다.
          </ScenarioStep>
          <ScenarioStep index={2} title="실행 검증">
            타임박스를 시작하고 실패 체크인과 10분 재시작 흐름을 직접 눌러봅니다.
          </ScenarioStep>
          <ScenarioStep index={3} title="결과 확인">
            Recovery24, TTR, lastProcessedDate, active alert를 같은 화면에서 확인합니다.
          </ScenarioStep>
        </View>
      </View>

      <View style={styles.footer}>
        <Text style={styles.footerHint}>
          user와 날짜를 먼저 저장하면 나머지 단계가 그 컨텍스트를 그대로 사용합니다.
        </Text>
        <TouchableOpacity style={styles.primaryButton} onPress={() => onStageChange(1)}>
          <Text style={styles.primaryButtonText}>계획 단계로 이동</Text>
        </TouchableOpacity>
      </View>
    </ScrollView>
  );
}

function ScenarioStep({ index, title, children }: { index: number; title: string; children: React.ReactNode }) {
  return (
    <View style={styles.scenarioStep}>
      <Text style={styles.scenarioIndex}>{index}</Text>
      <View style={styles.scenarioBody}>
        <Text style={styles.scenarioTitle}>{title}</Text>
        <Text style={styles.scenarioText}>{children}</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f9f9f9' },
  content: { padding: 16, paddingBottom: 40 },
  stageIntro: { marginBottom: 24 },
  eyebrow: { fontSize: 12, fontWeight: 'bold', color: '#666', marginBottom: 4 },
  h2: { fontSize: 20, fontWeight: 'bold', color: '#111', marginBottom: 8, lineHeight: 28 },
  note: { fontSize: 13, color: '#555', lineHeight: 20 },
  card: { backgroundColor: '#fff', borderRadius: 12, padding: 16, marginBottom: 16, shadowColor: '#000', shadowOpacity: 0.05, shadowRadius: 4, elevation: 2 },
  cardHead: { marginBottom: 16 },
  cardTitle: { fontSize: 18, fontWeight: 'bold', color: '#111', marginBottom: 4 },
  form: { gap: 16 },
  field: { gap: 8 },
  label: { fontSize: 14, fontWeight: '600', color: '#333' },
  input: { borderWidth: 1, borderColor: '#ccc', borderRadius: 8, padding: 12, fontSize: 16, backgroundColor: '#fafafa' },
  primaryButton: { backgroundColor: '#111', paddingVertical: 14, borderRadius: 8, alignItems: 'center' },
  primaryButtonText: { color: '#fff', fontSize: 16, fontWeight: 'bold' },
  scenarioList: { gap: 16 },
  scenarioStep: { flexDirection: 'row', gap: 12 },
  scenarioIndex: { width: 24, height: 24, borderRadius: 12, backgroundColor: '#eee', textAlign: 'center', lineHeight: 24, fontSize: 12, fontWeight: 'bold', color: '#333' },
  scenarioBody: { flex: 1 },
  scenarioTitle: { fontSize: 15, fontWeight: 'bold', color: '#111', marginBottom: 4 },
  scenarioText: { fontSize: 14, color: '#555', lineHeight: 20 },
  footer: { marginTop: 16, gap: 12 },
  footerHint: { fontSize: 13, color: '#666', textAlign: 'center', paddingHorizontal: 16 },
});
