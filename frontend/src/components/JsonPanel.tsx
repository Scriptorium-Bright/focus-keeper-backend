import React from 'react';
import { View, Text, StyleSheet, ScrollView } from 'react-native';
import { pretty } from '../utils';

interface JsonPanelProps {
  title: string;
  value: unknown;
  emptyText?: string;
}

export function JsonPanel({ title, value, emptyText = "아직 조회하지 않았습니다." }: JsonPanelProps) {
  return (
    <View style={styles.container}>
      <Text style={styles.title}>{title}</Text>
      <ScrollView horizontal style={styles.preContainer}>
        <Text style={styles.preText}>
          {value == null ? emptyText : pretty(value)}
        </Text>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    backgroundColor: '#1e1e1e',
    borderRadius: 8,
    padding: 12,
    marginVertical: 8,
  },
  title: {
    color: '#a9b7c6',
    fontSize: 14,
    fontWeight: 'bold',
    marginBottom: 8,
  },
  preContainer: {
    backgroundColor: '#2b2b2b',
    borderRadius: 6,
    padding: 10,
  },
  preText: {
    color: '#a9b7c6',
    fontFamily: 'monospace',
    fontSize: 12,
  },
});
